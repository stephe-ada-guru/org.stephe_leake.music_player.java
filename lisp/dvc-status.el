;;; dvc-status.el --- A generic status mode for DVC

;; Copyright (C) 2007 by all contributors

;; Author: Stephen Leake, <stephen_leake@stephe-leake.org>

;; This file is free software; you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 2, or (at your option)
;; any later version.

;; This file is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.

;; You should have received a copy of the GNU General Public License
;; along with GNU Emacs; see the file COPYING.  If not, write to
;; the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
;; Boston, MA 02110-1301, USA.

;;; Commentary:

;;

;;; Code:

(require 'dvc-ui)
(require 'dvc-defs)
(require 'dvc-core)
(require 'dvc-fileinfo)
(require 'uniquify)

(defcustom dvc-status-display-known nil
  "If non-nil, display files with 'known' status in xmtn-status buffer."
  :type 'boolean
  :group 'dvc)

(defcustom dvc-status-display-ignored nil
  "If non-nil, display files with 'ignored' status in xmtn-status buffer."
  :type 'boolean
  :group 'dvc)

;; FIXME: this should be in dvc-ui or somewhere.
;; or use "keyboard menu"; see elisp info 22.17.3 Menus and the Keyboard
(defun dvc-offer-choices (comment choices)
  "Present user with a choice of actions, labeled by COMMENT. CHOICES is a list of pairs
containing (symbol description)."
  (let ((msg "use ")
        choice)
    (dolist (choice choices)
      (setq msg (concat msg
                        (key-description (car (where-is-internal (car choice))))
                        " (" (cadr choice) ") ")))
    (error (if comment
               (concat comment "; " msg)
             msg))))

(defvar dvc-status-mode-map
  (let ((map (make-sparse-keymap)))
    ;; grouped by major function, then alphabetical by dvc-keyvec name
    ;; workspace operations
    (define-key map dvc-keyvec-add      'dvc-status-add-files)
    (define-key map dvc-keyvec-commit   'dvc-log-edit)
    (define-key map dvc-keyvec-ediff    'dvc-status-ediff)
    (define-key map dvc-keyvec-help     'describe-mode)
    (define-key map dvc-keyvec-logs     'dvc-log)
    (define-key map dvc-keyvec-mark     'dvc-fileinfo-mark-file)
    (define-key map dvc-keyvec-mark-all 'dvc-fileinfo-mark-all)
    (define-key map dvc-keyvec-next     'dvc-fileinfo-next)
    (define-key map dvc-keyvec-previous 'dvc-fileinfo-prev)
    (define-key map dvc-keyvec-quit     'dvc-buffer-quit)
    (define-key map dvc-keyvec-refresh  'dvc-generic-refresh)
    (define-key map dvc-keyvec-revert   'dvc-status-revert-files)
    (define-key map dvc-keyvec-unmark   'dvc-fileinfo-unmark-file)
    (define-key map dvc-keyvec-unmark-all   'dvc-fileinfo-unmark-all)
    (define-key map [?i]                'dvc-status-ignore-files) ; FIXME: ?i collides with dvc-key-inventory
    (define-key map [?I]                'dvc-ignore-file-extensions-in-dir)
    (define-key map "\M-I"              'dvc-ignore-file-extensions)
    (define-key map [?k]                'dvc-fileinfo-kill)
    (define-key map dvc-keyvec-remove   'dvc-status-remove-files)
    (define-key map "\r"                'dvc-find-file-other-window)
    (define-key map "\M-d"              'dvc-status-dtrt)

    ;; database operations
    (define-key map (dvc-prefix-merge ?u) 'dvc-update)
    ;; (define-key map (dvc-prefix-merge ?s) 'dvc-sync) FIXME: need dvc-sync
    (define-key map (dvc-prefix-merge ?m) 'dvc-missing)
    (define-key map (dvc-prefix-merge ?M) 'dvc-merge)

    ;; advanced
    (define-key map (dvc-prefix-tagging-method ?e) 'dvc-edit-ignore-files)
    (define-key map (dvc-prefix-buffer ?p) 'dvc-show-process-buffer)
    (define-key map (dvc-prefix-buffer ?L) 'dvc-open-internal-log-buffer)

    map)
  "Keymap used in `dvc-status-mode'.")

(easy-menu-define dvc-status-mode-menu dvc-status-mode-map
  "`dvc-status' menu"
  ;; FIXME: add all keymap operations to menu
  `("DVC"
    ["Refresh Buffer"          dvc-generic-refresh        t]
    ["Edit log before commit"  dvc-status-edit-log        t]
    ("Merge"
     ["Update"                 dvc-update                 t]
     ;; ["Sync"                   dvc-sync                   t] FIXME: need dvc-sync
     ["Show missing"           dvc-missing                t]
     ["Merge"                  dvc-merge                  t]
     )
    ("Ignore"
     ["Ignore Files"           dvc-status-ignore-files    t]
     ["Ignore Extensions in dir" dvc-ignore-file-extensions-in-dir t]
     ["Ignore Extensions globally" dvc-ignore-file-extensions t]
     ["Edit Ignore File"       dvc-edit-ignore-files      t]
     )
    ["Ediff File"              dvc-status-ediff           t]
    ["Delete File"             dvc-status-remove-files    t]
    ["Revert File"             dvc-status-revert-files    t]
    ))

(define-derived-mode dvc-status-mode dvc-fundamental-mode "dvc-status"
  "Major mode to display workspace status."
  (setq dvc-buffer-current-active-dvc (dvc-current-active-dvc))
  (setq dvc-fileinfo-ewoc (ewoc-create 'dvc-fileinfo-printer))
  (set (make-local-variable 'dvc-get-file-info-at-point-function) 'dvc-fileinfo-current-file)
  (setq dvc-buffer-marked-file-list nil)
  (use-local-map dvc-status-mode-map)
  (easy-menu-add dvc-status-mode-menu)
  (dvc-install-buffer-menu)
  (setq buffer-read-only t)
  (buffer-disable-undo)
  (set-buffer-modified-p nil))

(add-to-list 'uniquify-list-buffers-directory-modes 'dvc-status-mode)

(defun dvc-status-prepare-buffer (dvc root base-revision branch header-more refresh)
  "Prepare and return a status buffer. Should be called by <back-end>-dvc-status.
DVC is back-end.
ROOT is absolute path to workspace.
BASE-REVISION is a string identifying the workspace's base revision.
BRANCH is a string identifying the workspace's branch.
HEADER-MORE is a function called to add other text to the ewoc header;
it should return a string, which is added to the header with princ.
REFRESH is a function that refreshes the status; see `dvc-buffer-refresh-function'."

  (let ((status-buffer (dvc-get-buffer-create dvc 'status root)))
    (dvc-kill-process-maybe status-buffer)
    (with-current-buffer status-buffer
      (let ((inhibit-read-only t)) (erase-buffer))
      (dvc-status-mode)
      (let ((header (with-output-to-string
                      (princ (format "Status for %s:\n" root))
                      (princ (format "  base revision : %s\n" base-revision))
                      (princ (format "  branch        : %s\n" branch))
                      (if (functionp header-more) (princ (funcall header-more)))))
            (footer ""))
        (set (make-local-variable 'dvc-buffer-refresh-function) refresh)
        (ewoc-filter dvc-fileinfo-ewoc (lambda (elem) nil))
        (ewoc-set-hf dvc-fileinfo-ewoc header footer)
        (ewoc-enter-last dvc-fileinfo-ewoc (make-dvc-fileinfo-message :text (format "Running %s..." dvc)))
        (ewoc-refresh dvc-fileinfo-ewoc)))
    (dvc-switch-to-buffer-maybe status-buffer)))

(defun dvc-status-dtrt ()
  "Do The Right Thing in a status buffer; update, commit, resolve
conflicts, and/or ediff current files."
  (interactive)

  (let (status)
    ;; Note that message elements cannot be marked. Make sure all
    ;; selected files need the same action.
    (if (< 1 (length dvc-buffer-marked-file-list))
        (ewoc-map (lambda (fileinfo)
                    (etypecase fileinfo
                      (dvc-fileinfo-message
                       nil)

                      (dvc-fileinfo-file ; also matches dvc-fileinfo-dir
                       (if status
                           (if (not (equal status (dvc-fileinfo-file-status fileinfo)))
                               (error "cannot Do The Right Thing on files with different status"))
                         (setq status (dvc-fileinfo-file-status fileinfo)))
                       ;; don't redisplay the element
                       nil)))
                  dvc-fileinfo-ewoc)
      (setq status (dvc-fileinfo-file-status (dvc-fileinfo-current-fileinfo))))

    (ecase status
      (added
       ;; Don't offer Remove here; not a common action. Just start or
       ;; continue a commit log entry.
       (dvc-log-edit t t))

      (deleted
       (dvc-log-edit t t))

      (missing
       ;; File is in database, but not in workspace
       (ding)
       (dvc-offer-choices (concat (dvc-fileinfo-current-file) " does not exist in working directory")
                          '((dvc-status-revert-files "revert")
                            (dvc-status-remove-files "remove"))))

      (modified
       ;; Don't offer undo here; not a common action
       ;; Assume user has started the commit log frame
       (if (< 1 (length dvc-buffer-marked-file-list))
           (error "cannot diff more than one file"))
       (dvc-status-ediff))

      ((or rename-source rename-target)
       (dvc-log-edit t t))

      (unknown
       (dvc-offer-choices nil
                          '((dvc-add "add")
                            (dvc-status-ignore-files "ignore")
                            (dvc-delete "delete"))))
      )))

(defun dvc-status-inventory-done (status-buffer)
  (with-current-buffer status-buffer
    (ewoc-enter-last dvc-fileinfo-ewoc (make-dvc-fileinfo-message :text "Parsing inventory..."))
    (ewoc-refresh dvc-fileinfo-ewoc)
    (redisplay t)
    ;; delete "running", "parsing" from the ewoc now, but don't
    ;; refresh until the status is displayed
    (dvc-fileinfo-delete-messages)))

;; diff operations
(defun dvc-status-diff ()
  "Run diff of the current workspace file against the database version."
  (interactive)
  (let ((fileinfo (dvc-fileinfo-current-fileinfo))
        (file (concat (dvc-fileinfo-file-dir fileinfo) (dvc-fileinfo-file-file fileinfo))))
    ;; FIXME: need user interface to specify other revision to diff against
    ;; FIXME: dvc-file-diff defaults to buffer-file; change to default to (dvc-get-file-info-at-point)
    ;; default dvc-get-file-info-at-point to (buffer-file-name) for non-dvc buffers
    (dvc-file-diff file)))

(defun dvc-status-ediff ()
  "Run ediff on the current workspace file, against the database version."
  (interactive)
  ;; FIXME: need user interface to specify other revision to diff against
  (let ((dvc-temp-current-active-dvc dvc-buffer-current-active-dvc))
    (dvc-file-ediff (dvc-fileinfo-current-file))))

;; database operations
(defun dvc-status-add-files ()
  "Add current files to the database. Directories are also added,
but not recursively."
  (interactive)
  (let* ((files (dvc-current-file-list))
         (filtered files))
    (dolist (file files)
      ;; FIXME: xmtn-dvc.el xmtn--add-files says on directories, "mtn
      ;; add" will recurse, which isn't what we want. but that's not
      ;; true for current monotone. bzr also does not recurse.
      ;;
      ;; Note that there is no "add recursive" DVC command. Selecting
      ;; all the files in a directory is the prefered approach.
      (if (file-directory-p file)
          (setq filtered (delete file filtered))))
    (apply 'dvc-add-files filtered))

  ;; Update the ewoc status of each added file to 'added'; this avoids
  ;; the need to run the backend again. Assume any directories that
  ;; were filtered out above were added because there were files in
  ;; them. FIXME: should verify that here.
  (if (= 0 (length dvc-buffer-marked-file-list))
      ;; no marked files
      (let ((fileinfo (dvc-fileinfo-current-fileinfo)))
        (setf (dvc-fileinfo-file-status fileinfo) 'added)
        (ewoc-invalidate dvc-fileinfo-ewoc (ewoc-locate dvc-fileinfo-ewoc)))
    ;; marked files
    (ewoc-map (lambda (fileinfo)
                (etypecase fileinfo
                  (dvc-fileinfo-message
                   nil)

                  (dvc-fileinfo-file ; also matches dvc-fileinfo-dir
                   (if (dvc-fileinfo-file-mark fileinfo) (setf (dvc-fileinfo-file-status fileinfo) 'added)))))
              dvc-fileinfo-ewoc)))

(defun dvc-status-ignore-files ()
  "Ignore current files."
  (interactive)
  (dvc-ignore-files (dvc-current-file-list))

  ;; kill the files from the ewoc, since we are ignoring them; this
  ;; avoids the need to run the backend again.
  (if (= 0 (length dvc-buffer-marked-file-list))
      ;; no marked files
      (progn
        ;; binding inhibit-read-only doesn't seem to work here
        (toggle-read-only 0)
        (ewoc-delete dvc-fileinfo-ewoc (ewoc-locate dvc-fileinfo-ewoc))
        (toggle-read-only 1))
    ;; marked files
    (setq dvc-buffer-marked-file-list nil)
    (ewoc-filter dvc-fileinfo-ewoc
                 (lambda (fileinfo)
                     (not (dvc-fileinfo-file-mark fileinfo)))
                 )))

(defun dvc-status-remove-files ()
  "Remove current files."
  (interactive)
  (apply 'dvc-remove-files (dvc-current-file-list))

  ;; kill the files from the ewoc, since we are removing them; this
  ;; avoids the need to run the backend again.
  (if (= 0 (length dvc-buffer-marked-file-list))
      ;; no marked files
      (let ((inhibit-read-only t))
        (ewoc-delete dvc-fileinfo-ewoc (ewoc-locate dvc-fileinfo-ewoc)))
    ;; marked files
    (setq dvc-buffer-marked-file-list nil)
    (ewoc-filter dvc-fileinfo-ewoc
                 (lambda (fileinfo)
                   (etypecase fileinfo
                     (dvc-fileinfo-message
                      nil)

                     (dvc-fileinfo-file ; also matches dvc-fileinfo-dir
                      (not (dvc-fileinfo-file-mark fileinfo)))))
                 )))

(defun dvc-status-revert-files ()
  "Revert current files."
  (interactive)
  (apply 'dvc-revert-files (dvc-current-file-list))

  ;; kill the files from the ewoc, since they are now up-to-date; this
  ;; avoids the need to run the backend again.
  (if (= 0 (length dvc-buffer-marked-file-list))
      ;; no marked files
      (let ((inhibit-read-only t))
        (ewoc-delete dvc-fileinfo-ewoc (ewoc-locate dvc-fileinfo-ewoc)))
    ;; marked files
    (setq dvc-buffer-marked-file-list nil)
    (ewoc-filter dvc-fileinfo-ewoc
                 (lambda (fileinfo)
                   (etypecase fileinfo
                     (dvc-fileinfo-message
                      nil)

                     (dvc-fileinfo-file ; also matches dvc-fileinfo-dir
                      (not (dvc-fileinfo-file-mark fileinfo)))))
                 )))

(provide 'dvc-status)
;;; end of file
