;;; xmtn-status.el --- manage actions for multiple projects

;; Copyright (C) 2009 Stephen Leake

;; Author: Stephen Leake
;; Keywords: tools

;; This file is free software; you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 2 of the License, or
;; (at your option) any later version.
;;
;; This file is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this file; see the file COPYING.  If not, write to
;; the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
;; Boston, MA  02110-1301  USA.

(eval-and-compile
  ;; these have macros we use
  (require 'xmtn-ids))

(eval-when-compile
  ;; these have functions we use
  (require 'xmtn-base)
  (require 'xmtn-conflicts))

(defvar xmtn-status-root ""
  "Buffer-local variable holding root directory.")
(make-variable-buffer-local 'xmtn-status-root)
(put 'xmtn-status-root 'permanent-local t)

(defvar xmtn-status-ewoc nil
  "Buffer-local ewoc for displaying propagations.
All xmtn-status functions operate on this ewoc.
The elements must all be of class xmtn-status-data.")
(make-variable-buffer-local 'xmtn-status-ewoc)
(put 'xmtn-status-ewoc 'permanent-local t)

(defstruct (xmtn-status-data (:copier nil))
  work             ; directory name relative to xmtn-status-root
  branch           ; branch name (assumed never changes)
  need-refresh     ; nil | t : if an async process was started that invalidates state data
  head-rev         ; nil | mtn rev string : current head revision, nil if multiple heads
  conflicts-buffer ; *xmtn-conflicts* buffer for merge
  heads            ; 'at-head | 'need-update | 'need-merge)
  (local-changes
   'need-scan)     ; 'need-scan | 'need-commit | 'ok
  (conflicts
   'need-scan)     ; 'need-scan | 'need-resolve | 'need-review-resolve-internal | 'resolved | 'none
  )

(defun xmtn-status-work (data)
  (concat xmtn-status-root (xmtn-status-data-work data)))

(defun xmtn-status-need-refresh (elem data)
  (setf (xmtn-status-data-need-refresh data) t)
  (ewoc-invalidate xmtn-status-ewoc elem))

(defun xmtn-status-printer (data)
  "Print an ewoc element."
  (insert (dvc-face-add (format "%s\n" (xmtn-status-data-work data)) 'dvc-keyword))

  (if (xmtn-status-data-need-refresh data)
      (insert (dvc-face-add "  need refresh\n" 'dvc-conflict))

    (ecase (xmtn-status-data-local-changes data)
      (need-scan (insert "  from local changes unknown\n"))
      (need-commit (insert (dvc-face-add "  need dvc-status\n" 'dvc-header)))
      (ok nil))

    (ecase (xmtn-status-data-conflicts data)
      (need-scan
       (insert "conflicts need scan\n"))
      (need-resolve
       (insert (dvc-face-add "  need resolve conflicts\n" 'dvc-conflict)))
      (need-review-resolve-internal
       (insert (dvc-face-add "  need review resolve internal\n" 'dvc-header)))
      (resolved
       (insert "  conflicts resolved\n"))
      ((resolved none) nil))

    (ecase (xmtn-status-data-heads data)
      (at-head     nil)
      (need-update (insert (dvc-face-add "  need dvc-missing\n" 'dvc-conflict)))
      (need-merge
       (insert (dvc-face-add "  need dvc-merge\n" 'dvc-conflict)))
      )))

(defun xmtn-status-clean ()
  "Clean current workspace, delete from ewoc"
  (interactive)
  (let* ((elem (ewoc-locate xmtn-status-ewoc))
         (data (ewoc-data elem))
         (inhibit-read-only t))
    (xmtn-conflicts-clean (xmtn-status-work data))
    (ewoc-delete xmtn-status-ewoc elem)))

(defun xmtn-status-cleanp ()
  "Non-nil if clean & quit is appropriate for current workspace."
  (let ((data (ewoc-data (ewoc-locate xmtn-status-ewoc))))
    (and (not (xmtn-status-data-need-refresh data))
         (eq 'ok (xmtn-status-data-local-changes data))
         (eq 'at-head (xmtn-status-data-heads data)))))

(defun xmtn-status-do-refresh-one ()
  (interactive)
  (let* ((elem (ewoc-locate xmtn-status-ewoc))
         (data (ewoc-data elem)))
    (xmtn-status-refresh-one data)
    (ewoc-invalidate xmtn-status-ewoc elem)))

(defun xmtn-status-refreshp ()
  "Non-nil if refresh is appropriate for current workspace."
  (let ((data (ewoc-data (ewoc-locate xmtn-status-ewoc))))
    (xmtn-status-data-need-refresh data)))

(defun xmtn-status-update ()
  "Update current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-status-ewoc))
         (data (ewoc-data elem)))
    (xmtn-status-need-refresh elem data)
    (let ((default-directory (xmtn-status-work data)))
      (xmtn-dvc-update))
    (xmtn-status-refresh-one data)
    (ewoc-invalidate xmtn-status-ewoc elem)))

(defun xmtn-status-updatep ()
  "Non-nil if update is appropriate for current workspace."
  (let ((data (ewoc-data (ewoc-locate xmtn-status-ewoc))))
    (and (not (xmtn-status-data-need-refresh data))
         (eq 'need-update (xmtn-status-data-heads data)))))

(defun xmtn-status-resolve-conflicts ()
  "Resolve conflicts for current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-status-ewoc))
         (data (ewoc-data elem)))
    (setf (xmtn-status-data-conflicts data) 'resolved)
    (xmtn-status-need-refresh elem data)
    (pop-to-buffer (xmtn-status-data-conflicts-buffer data))))

(defun xmtn-status-resolve-conflictsp ()
  "Non-nil if resolve conflicts is appropriate for current workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-status-ewoc))))
    (and (not (xmtn-status-data-need-refresh data))
         (member (xmtn-status-data-conflicts data)
                 '(need-resolve need-review-resolve-internal)))))

(defun xmtn-status-status ()
  "Run xmtn-status on current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-status-ewoc))
         (data (ewoc-data elem)))
    (xmtn-status-need-refresh elem data)
    (setf (xmtn-status-data-local-changes data) 'ok)
    (xmtn-status (xmtn-status-work data))))

(defun xmtn-status-status-ok ()
  "Ignore local changes in current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-status-ewoc))
         (data (ewoc-data elem)))
    (setf (xmtn-status-data-local-changes data) 'ok)

    (if (buffer-live-p (xmtn-status-data-conflicts-buffer data))
        ;; creating the log-edit buffer requires a single status/diff/conflicts buffer
        (kill-buffer (xmtn-status-data-conflicts-buffer data)))

    (ewoc-invalidate xmtn-status-ewoc elem)))

(defun xmtn-status-statusp ()
  "Non-nil if xmtn-status is appropriate for current workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-status-ewoc))))
    (and (not (xmtn-status-data-need-refresh data))
         (member (xmtn-status-data-local-changes data)
                 '(need-scan need-commit)))))

(defun xmtn-status-missing ()
  "Run xmtn-missing on current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-status-ewoc))
         (data (ewoc-data elem)))
    (xmtn-status-need-refresh elem data)
    (xmtn-missing nil (xmtn-status-work data))))

(defun xmtn-status-missingp ()
  "Non-nil if xmtn-missing is appropriate for current workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-status-ewoc))))
    (and (not (xmtn-status-data-need-refresh data))
         (eq 'need-update (xmtn-status-data-heads data)))))

(defun xmtn-status-merge ()
  "Run dvc-merge on current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-status-ewoc))
         (data (ewoc-data elem))
         (default-directory (xmtn-status-work data)))
    (xmtn-status-need-refresh elem data)
    (xmtn-dvc-merge-1 default-directory nil)))

(defun xmtn-status-heads ()
  "Run xmtn-heads on current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-status-ewoc))
         (data (ewoc-data elem))
         (default-directory (xmtn-status-work data)))
    (xmtn-status-need-refresh elem data)
    (xmtn-view-heads-revlist)))

(defun xmtn-status-headsp ()
  "Non-nil if xmtn-heads is appropriate for current workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-status-ewoc))))
    (and (not (xmtn-status-data-need-refresh data))
         (eq 'need-merge (xmtn-status-data-heads data)))))

(defvar xmtn-status-actions-map
  (let ((map (make-sparse-keymap "actions")))
    (define-key map [?c]  '(menu-item "c) clean/delete"
                                      xmtn-status-clean
                                      :visible (xmtn-status-cleanp)))
    (define-key map [?g]  '(menu-item "g) refresh"
                                      xmtn-status-do-refresh-one
                                      :visible (xmtn-status-refreshp)))
    (define-key map [?i]  '(menu-item "i) ignore local changes"
                                      xmtn-status-status-ok
                                      :visible (xmtn-status-statusp)))
    (define-key map [?5]  '(menu-item "5) update"
                                      xmtn-status-update
                                      :visible (xmtn-status-updatep)))
    (define-key map [?4]  '(menu-item "4) xmtn-merge"
                                      xmtn-status-merge
                                      :visible (xmtn-status-headsp)))
    (define-key map [?3]  '(menu-item "3) xmtn-heads"
                                      xmtn-status-heads
                                      :visible (xmtn-status-headsp)))
    (define-key map [?2]  '(menu-item "2) resolve conflicts"
                                      xmtn-status-resolve-conflicts
                                      :visible (xmtn-status-resolve-conflictsp)))
    (define-key map [?1]  '(menu-item "1) dvc-missing"
                                      xmtn-status-missing
                                      :visible (xmtn-status-missingp)))
    (define-key map [?0]  '(menu-item "0) status"
                                      xmtn-status-status
                                      :visible (xmtn-status-statusp)))
    map)
  "Keyboard menu keymap used in multiple-status mode.")

(dvc-make-ewoc-next xmtn-status-next xmtn-status-ewoc)
(dvc-make-ewoc-prev xmtn-status-prev xmtn-status-ewoc)

(defvar xmtn-multiple-status-mode-map
  (let ((map (make-sparse-keymap)))
    (define-key map "\M-d" xmtn-status-actions-map)
    (define-key map [?g]  'xmtn-status-refresh)
    (define-key map [?n]  'xmtn-status-next)
    (define-key map [?p]  'xmtn-status-prev)
    (define-key map [?q]  (lambda () (interactive) (kill-buffer (current-buffer))))
    map)
  "Keymap used in `xmtn-multiple-status-mode'.")

(define-derived-mode xmtn-multiple-status-mode nil "xmtn-multiple-status"
  "Major mode to show status of multiple workspaces."
  (setq dvc-buffer-current-active-dvc 'xmtn)
  (setq buffer-read-only nil)

  ;; don't do normal clean up stuff
  (set (make-local-variable 'before-save-hook) nil)
  (set (make-local-variable 'write-file-functions) nil)

  (dvc-install-buffer-menu)
  (setq buffer-read-only t)
  (buffer-disable-undo)

  (set-buffer-modified-p nil)
  (xmtn-status-refresh)
  (xmtn-status-next))

(defun xmtn-status-local-changes (work)
  "Value for xmtn-status-data-local-changes for WORK."
  (message "checking %s for local changes" work)
  (let ((default-directory work))

    (dvc-run-dvc-sync
     'xmtn
     (list "status")
     :finished (lambda (output error status arguments)
                 ;; we don't get an error status for not up-to-date,
                 ;; so parse the output.
                 ;; FIXME: add option to automate inventory to just return status; can return on first change
                 ;; FIXME: 'patch' may be internationalized.

                 (message "") ; clear minibuffer
                 (set-buffer output)
                 (goto-char (point-min))
                 (if (search-forward "patch" (point-max) t)
                     'need-commit
                   'ok))

     :error (lambda (output error status arguments)
              (pop-to-buffer error))))
  )

(defun xmtn-status-conflicts (data)
  "Return value for xmtn-status-data-conflicts for DATA."
  ;; Can't check for "current heads", since there could be more than
  ;; 2, so just recreate conflicts
  (let* ((work (xmtn-status-work data))
         (default-directory work))

    (if (buffer-live-p (xmtn-status-data-conflicts-buffer data))
        (kill-buffer (xmtn-status-data-conflicts-buffer data)))

    ;; create conflicts file
    (xmtn-conflicts-clean work)
    (xmtn-conflicts-save-opts work work (xmtn-status-data-branch data) (xmtn-status-data-branch data))
    (dvc-run-dvc-sync
     'xmtn
     (list "conflicts" "store")
     :error (lambda (output error status arguments)
              (pop-to-buffer error)))

    ;; create conflicts buffer
    (setf (xmtn-status-data-conflicts-buffer data)
          (save-excursion
            (let ((dvc-switch-to-buffer-first nil))
              (xmtn-conflicts-review work)
              (current-buffer))))

    (with-current-buffer (xmtn-status-data-conflicts-buffer data)
      (case xmtn-conflicts-total-count
        (0 'none)
        (t
         (if (= xmtn-conflicts-total-count xmtn-conflicts-resolved-internal-count)
             'need-review-resolve-internal
           'need-resolve))))))

(defun xmtn-status-refresh-one (data)
  "Refresh DATA."
  (let ((work (xmtn-status-work data)))

    (message "checking heads for %s " work)

    (let ((heads (xmtn--heads work (xmtn-status-data-branch data)))
          (base-rev (xmtn--get-base-revision-hash-id-or-null work)))
      (case (length heads)
        (1
         (setf (xmtn-status-data-head-rev data) (nth 0 heads))
         (setf (xmtn-status-data-conflicts data) 'none)
         (if (string= (xmtn-status-data-head-rev data) base-rev)
             (setf (xmtn-status-data-heads data) 'at-head)
           (setf (xmtn-status-data-heads data) 'need-update)))
        (t
         (setf (xmtn-status-data-head-rev data) nil)
         (setf (xmtn-status-data-heads data) 'need-merge)
         (case (xmtn-status-data-conflicts data)
           (resolved
            ;; Assume the resolution was just completed, so don't erase it!
            nil)
           (t
            (setf (xmtn-status-data-conflicts data) 'need-scan))))))

    (message "")

    (case (xmtn-status-data-local-changes data)
      (need-scan
       (setf (xmtn-status-data-local-changes data) (xmtn-status-local-changes work)))
      (t nil))

    (ecase (xmtn-status-data-conflicts data)
      (need-scan
       (setf (xmtn-status-data-conflicts data)
             (xmtn-status-conflicts data)))
      (t nil))

    (setf (xmtn-status-data-need-refresh data) nil))

  ;; return non-nil to refresh display as we go along
  t)

(defun xmtn-status-refresh ()
  "Refresh status of each ewoc element."
  (interactive)
  (ewoc-map 'xmtn-status-refresh-one xmtn-status-ewoc)
  (message "done"))

;;;###autoload
(defun xmtn-status-multiple (dir)
  "Show actions to update all projects under DIR."
  (interactive "DStatus for all (root directory): ")
  (let ((default-directory dir)
        (workspaces (xmtn--filter-non-dir dir)))

    (pop-to-buffer (get-buffer-create "*xmtn-multi-status*"))
    (setq xmtn-status-root (file-name-as-directory dir))
    (setq xmtn-status-ewoc (ewoc-create 'xmtn-status-printer))
    (let ((inhibit-read-only t)) (delete-region (point-min) (point-max)))
    (ewoc-set-hf xmtn-status-ewoc (format "Root : %s\n" xmtn-status-root) "")
    (dolist (workspace workspaces)
      (ewoc-enter-last xmtn-status-ewoc
                       (make-xmtn-status-data
                        :work workspace
                        :branch (xmtn--tree-default-branch (concat xmtn-status-root workspace))
                        :need-refresh t))))
    (xmtn-multiple-status-mode))

;;;###autoload
(defun xmtn-status-one (work)
  "Show actions to update WORK."
  (interactive "DStatus for (workspace): \n")
  (let ((default-directory work))
    (pop-to-buffer (get-buffer-create "*xmtn-multi-status*"))
    (setq xmtn-status-root (expand-file-name (concat (file-name-as-directory work) "../")))
    (setq xmtn-status-ewoc (ewoc-create 'xmtn-status-printer))
    (let ((inhibit-read-only t)) (delete-region (point-min) (point-max)))
    (ewoc-set-hf xmtn-status-ewoc (format "Root : %s\n" xmtn-status-root) "")
    (ewoc-enter-last xmtn-status-ewoc
                     (make-xmtn-status-data
                      :work (file-name-nondirectory (directory-file-name work))
                      :branch (xmtn--tree-default-branch default-directory)
                      :need-refresh t))
    (xmtn-multiple-status-mode)))

(provide 'xmtn-multi-status)

;; end of file
