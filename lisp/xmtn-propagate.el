;;; xmtn-propagate.el --- manage multiple propagations for DVC backend for monotone

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
  (require 'xmtn-conflicts))

(defvar xmtn-propagate-from-root ""
  "Buffer-local variable holding `from' root directory.")
(make-variable-buffer-local 'xmtn-propagate-from-root)

(defvar xmtn-propagate-to-root ""
  "Buffer-local variable holding `to' root directory.")
(make-variable-buffer-local 'xmtn-propagate-to-root)

(defstruct (xmtn-propagate-data (:copier nil))
  from-work          ; directory name relative to xmtn-propagate-from-root
  to-work            ; directory name relative to xmtn-propagate-to-root
  need-refresh       ; nil | t; if an async process was started that invalidates state data
  from-rev           ; mtn rev string
  to-rev             ; mtn rev string
  conflicts-buffer   ; *xmtn-conflicts* buffer for this propagation
  propagate-needed   ; nil | t
  from-heads         ; 'at-head | 'need-update | 'need-merge)
  to-heads           ;
  (from-local-changes
   'need-scan)       ; 'need-scan | 'need-status | 'ok
  (to-local-changes
   'need-scan)       ;    once this is set to 'ok, no action changes it.
  (conflicts
   'need-scan)       ; 'need-scan | 'need-resolve | 'ok
  )

(defun xmtn-propagate-from-work (data)
  (concat xmtn-propagate-from-root (xmtn-propagate-data-from-work data)))

(defun xmtn-propagate-to-work (data)
  (concat xmtn-propagate-to-root (xmtn-propagate-data-to-work data)))

(defun xmtn-propagate-need-refresh (elem data)
  (setf (xmtn-propagate-data-need-refresh data) t)
  (ewoc-invalidate xmtn-propagate-ewoc elem))

(defun xmtn-propagate-printer (data)
  "Print an ewoc element."
  (if (string= (xmtn-propagate-data-from-work data)
               (xmtn-propagate-data-to-work data))
      (insert (dvc-face-add (format "%s\n" (xmtn-propagate-data-from-work data)) 'dvc-keyword))
    (insert (dvc-face-add (format "%s -> %s\n"
                                  (xmtn-propagate-data-from-work data)
                                  (xmtn-propagate-data-to-work data))
                          'dvc-keyword)))

  (if (xmtn-propagate-data-need-refresh data)
      (insert (dvc-face-add "  need refresh\n" 'dvc-conflict))

    (if (xmtn-propagate-data-propagate-needed data)
        (progn
          (ecase (xmtn-propagate-data-from-local-changes data)
            (need-scan (insert "  local changes unknown\n"))
            (need-status (insert (dvc-face-add "  need dvc-status from\n" 'dvc-header)))
            (ok nil))

          (ecase (xmtn-propagate-data-to-local-changes data)
            (need-scan (insert "  local changes unknown\n"))
            (need-status (insert (dvc-face-add "  need dvc-status to\n" 'dvc-header)))
            (ok nil))

          (ecase (xmtn-propagate-data-from-heads data)
            (at-head     nil)
            (need-update (insert (dvc-face-add "  need dvc-missing from\n" 'dvc-conflict)))
            (need-merge  (insert (dvc-face-add "  need xmtn-heads from\n" 'dvc-conflict))))

          (ecase (xmtn-propagate-data-to-heads data)
            (at-head     nil)
            (need-update (insert (dvc-face-add "  need dvc-missing to\n" 'dvc-conflict)))
            (need-merge  (insert (dvc-face-add "  need xmtn-heads to\n" 'dvc-conflict))))


          (if (and (eq 'at-head (xmtn-propagate-data-from-heads data))
                   (eq 'at-head (xmtn-propagate-data-to-heads data)))
              (ecase (xmtn-propagate-data-conflicts data)
                (need-scan (insert "conflicts need scan\n"))
                (need-resolve (insert (dvc-face-add "  need resolve conflicts\n" 'dvc-conflict)))
                (ok (insert (dvc-face-add "  need propagate\n" 'dvc-conflict)))))
          )

      ;; propagate not needed
      (ecase (xmtn-propagate-data-to-heads data)
       (at-head nil)
       (need-update (insert (dvc-face-add "  need dvc-update to\n" 'dvc-conflict)))
       (need-merge (insert (dvc-face-add "  programmer error!\n" 'dvc-conflict))))
      )))

(defvar xmtn-propagate-ewoc nil
  "Buffer-local ewoc for displaying propagations.
All xmtn-propagate functions operate on this ewoc.
The elements must all be of class xmtn-propagate-data.")
(make-variable-buffer-local 'xmtn-propagate-ewoc)

(defun xmtn-propagate-clean ()
  "Clean current workspace, delete from ewoc"
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (with-current-buffer (xmtn-propagate-data-conflicts-buffer data)
      (xmtn-conflicts-clean))
    (ewoc-delete xmtn-propagate-ewoc elem)))

(defun xmtn-propagate-cleanp ()
  "Non-nil if clean is appropriate for current workspace."
  (let ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (not (xmtn-propagate-data-propagate-needed data))
         (eq 'at-head (xmtn-propagate-data-to-heads data)))))

(defun xmtn-propagate-do-refresh-one ()
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (xmtn-propagate-refresh-one data)
    (ewoc-invalidate xmtn-propagate-ewoc elem)))

(defun xmtn-propagate-refreshp ()
  "Non-nil if refresh is appropriate for current workspace."
  (let ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (xmtn-propagate-data-need-refresh data)))

(defun xmtn-propagate-update ()
  "Update current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (xmtn-propagate-need-refresh elem data)
    (with-current-buffer (xmtn-propagate-data-conflicts-buffer data)
      (xmtn-dvc-update))
    (xmtn-propagate-refresh-one data)
    (ewoc-invalidate xmtn-propagate-ewoc elem)))

(defun xmtn-propagate-updatep ()
  "Non-nil if update is appropriate for current workspace."
  (let ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (not (xmtn-propagate-data-propagate-needed data))
         (eq 'need-update (xmtn-propagate-data-to-heads data)))))

(defun xmtn-propagate-propagate ()
  "Propagate current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (xmtn-propagate-need-refresh elem data)
    (with-current-buffer (xmtn-propagate-data-conflicts-buffer data)
      (xmtn-conflicts-do-propagate))
    (xmtn-propagate-refresh-one data)
    (ewoc-invalidate xmtn-propagate-ewoc elem)))

(defun xmtn-propagate-propagatep ()
  "Non-nil if propagate is appropriate for current workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (xmtn-propagate-data-propagate-needed data)
         (eq 'at-head (xmtn-propagate-data-from-heads data))
         (eq 'at-head (xmtn-propagate-data-to-heads data))
         (eq 'ok (xmtn-propagate-data-conflicts data)))))

(defun xmtn-propagate-resolve-conflicts ()
  "Resolve conflicts for current workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (xmtn-propagate-need-refresh elem data)
    (pop-to-buffer (xmtn-propagate-data-conflicts-buffer data))))

(defun xmtn-propagate-resolve-conflictsp ()
  "Non-nil if resolve conflicts is appropriate for current workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (xmtn-propagate-data-propagate-needed data)
         (eq 'at-head (xmtn-propagate-data-from-heads data))
         (eq 'at-head (xmtn-propagate-data-to-heads data))
         (eq 'need-resolve (xmtn-propagate-data-conflicts data)))))

(defun xmtn-propagate-status-to ()
  "Run xmtn-status on current `to' workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (xmtn-propagate-need-refresh elem data)
    (xmtn-status (xmtn-propagate-to-work data))))

(defun xmtn-propagate-status-to-ok ()
  "Ignore local changes in current `to' workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (setf (xmtn-propagate-data-to-local-changes data) 'ok)
    (ewoc-invalidate xmtn-propagate-ewoc elem)))

(defun xmtn-propagate-status-top ()
  "Non-nil if xmtn-status is appropriate for current `to' workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (xmtn-propagate-data-propagate-needed data)
         (eq 'need-status (xmtn-propagate-data-to-local-changes data)))))

(defun xmtn-propagate-status-from ()
  "Run xmtn-status on current `from' workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (xmtn-propagate-need-refresh elem data)
    (xmtn-status (xmtn-propagate-from-work data))))

(defun xmtn-propagate-status-from-ok ()
  "Ignore local changes in current `from' workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (setf (xmtn-propagate-data-from-local-changes data) 'ok)
    (ewoc-invalidate xmtn-propagate-ewoc elem)))

(defun xmtn-propagate-status-fromp ()
  "Non-nil if xmtn-status is appropriate for current `from' workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (xmtn-propagate-data-propagate-needed data)
         (eq 'need-status (xmtn-propagate-data-from-local-changes data)))))

(defun xmtn-propagate-missing-to ()
  "Run xmtn-missing on current `to' workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (xmtn-propagate-need-refresh elem data)
    (xmtn-missing nil (xmtn-propagate-to-work data))))

(defun xmtn-propagate-missing-top ()
  "Non-nil if xmtn-missing is appropriate for current `to' workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (xmtn-propagate-data-propagate-needed data)
         (eq 'need-update (xmtn-propagate-data-to-heads data)))))

(defun xmtn-propagate-missing-from ()
  "Run xmtn-missing on current `from' workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem)))
    (xmtn-propagate-need-refresh elem data)
    (xmtn-missing nil (xmtn-propagate-from-work data))))

(defun xmtn-propagate-missing-fromp ()
  "Non-nil if xmtn-missing is appropriate for current `from' workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (xmtn-propagate-data-propagate-needed data)
         (eq 'need-update (xmtn-propagate-data-from-heads data)))))

(defun xmtn-propagate-heads-to ()
  "Run xmtn-heads on current `to' workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem))
         (default-directory (xmtn-propagate-to-work data)))
    (xmtn-propagate-need-refresh elem data)
    (xmtn-view-heads-revlist)))

(defun xmtn-propagate-heads-top ()
  "Non-nil if xmtn-heads is appropriate for current `to' workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (xmtn-propagate-data-propagate-needed data)
         (eq 'need-merge (xmtn-propagate-data-to-heads data)))))

(defun xmtn-propagate-heads-from ()
  "Run xmtn-heads on current `from' workspace."
  (interactive)
  (let* ((elem (ewoc-locate xmtn-propagate-ewoc))
         (data (ewoc-data elem))
         (default-directory (xmtn-propagate-from-work data)))
    (xmtn-propagate-need-refresh elem data)
    (xmtn-view-heads-revlist)))

(defun xmtn-propagate-heads-fromp ()
  "Non-nil if xmtn-heads is appropriate for current `from' workspace."
  (let* ((data (ewoc-data (ewoc-locate xmtn-propagate-ewoc))))
    (and (not (xmtn-propagate-data-need-refresh data))
         (xmtn-propagate-data-propagate-needed data)
         (eq 'need-merge (xmtn-propagate-data-from-heads data)))))

(defvar xmtn-propagate-actions-map
  (let ((map (make-sparse-keymap "actions")))
    (define-key map [?c]  '(menu-item "c) clean/quit"
                                      xmtn-propagate-clean
                                      :visible (xmtn-propagate-cleanp)))
    (define-key map [?g]  '(menu-item "g) refresh"
                                      xmtn-propagate-do-refresh-one
                                      :visible (xmtn-propagate-refreshp)))
    (define-key map [?a]  '(menu-item "a) update"
                                      xmtn-propagate-update
                                      :visible (xmtn-propagate-updatep)))
    (define-key map [?9]  '(menu-item "9) propagate"
                                      xmtn-propagate-propagate
                                      :visible (xmtn-propagate-propagatep)))
    (define-key map [?8]  '(menu-item "8) resolve conflicts"
                                      xmtn-propagate-resolve-conflicts
                                      :visible (xmtn-propagate-resolve-conflictsp)))
    (define-key map [?7]  '(menu-item "7) ignore local changes to"
                                      xmtn-propagate-status-to-ok
                                      :visible (xmtn-propagate-status-top)))
    (define-key map [?6]  '(menu-item "6) ignore local changes from"
                                      xmtn-propagate-status-from-ok
                                      :visible (xmtn-propagate-status-fromp)))
    (define-key map [?5]  '(menu-item "5) status to"
                                      xmtn-propagate-status-to
                                      :visible (xmtn-propagate-status-top)))
    (define-key map [?4]  '(menu-item "4) status from"
                                      xmtn-propagate-status-from
                                      :visible (xmtn-propagate-status-fromp)))
    (define-key map [?3]  '(menu-item "3) dvc-missing to"
                                      xmtn-propagate-missing-to
                                      :visible (xmtn-propagate-missing-top)))
    (define-key map [?2]  '(menu-item "2) dvc-missing from"
                                      xmtn-propagate-missing-from
                                      :visible (xmtn-propagate-missing-fromp)))
    (define-key map [?1]  '(menu-item "1) xmtn-heads to"
                                      xmtn-propagate-heads-to
                                      :visible (xmtn-propagate-heads-top)))
    (define-key map [?0]  '(menu-item "0) xmtn-heads from"
                                      xmtn-propagate-heads-from
                                      :visible (xmtn-propagate-heads-fromp)))
    map)
  "Keyboard menu keymap used to manage propagates.")

(dvc-make-ewoc-next xmtn-propagate-next xmtn-propagate-ewoc)
(dvc-make-ewoc-prev xmtn-propagate-prev xmtn-propagate-ewoc)

(defvar xmtn-propagate-mode-map
  (let ((map (make-sparse-keymap)))
    (define-key map "\M-d" xmtn-propagate-actions-map)
    (define-key map [?g]  'xmtn-propagate-refresh)
    (define-key map [?n]  'xmtn-propagate-next)
    (define-key map [?p]  'xmtn-propagate-prev)
    (define-key map [?q]  (lambda () (interactive) (kill-buffer (current-buffer))))
    map)
  "Keymap used in `xmtn-propagate-mode'.")

(define-derived-mode xmtn-propagate-mode nil "xmtn-propagate"
  "Major mode to propagate multiple workspaces."
  (setq dvc-buffer-current-active-dvc 'xmtn)
  (setq buffer-read-only nil)
  (setq xmtn-propagate-ewoc (ewoc-create 'xmtn-propagate-printer))

  ;; don't do normal clean up stuff
  (set (make-local-variable 'before-save-hook) nil)
  (set (make-local-variable 'write-file-functions) nil)

  (dvc-install-buffer-menu)
  (setq buffer-read-only t)
  (buffer-disable-undo)
  (set-buffer-modified-p nil))

(defun xmtn-propagate-local-changes (work)
  "Value for xmtn-propagate-data-local-changes for WORK."
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
                     'need-status
                   'ok))

     :error (lambda (output error status arguments)
              (pop-to-buffer error))))
  )

(defun xmtn-propagate-needed (from-work from-rev to-rev)
  "t if branch in workspace FROM-WORK needs to be propagated to TO-WORK."
  (let ((result nil))

    (if (string= from-rev to-rev)
        nil
      ;; check for to descendant of from
      (let ((descendents (xmtn-automate-simple-command-output-lines from-work (list "descendents" from-rev)))
            (done nil))
        (if (not descendents)
            (setq result t)
          (while (and descendents (not done))
            (if (string= to-rev (car descendents))
                (progn
                  (setq result t)
                  (setq done t)))
            (setq descendents (cdr descendents))))))
    result
  ))

(defun xmtn-propagate-conflicts-buffer (from-work from-rev to-work to-rev)
  "Return a conflicts buffer for FROM-WORK, TO-WORK (absolute paths)."
  (let ((conflicts-buffer (dvc-get-buffer 'xmtn 'conflicts to-work)))

    (or conflicts-buffer
        (let ((default-directory to-work))
          (if (not (file-exists-p "_MTN/conflicts"))
              (progn
                ;; create conflicts file
                (xmtn-conflicts-save-opts from-work to-work)
                (dvc-run-dvc-sync
                 'xmtn
                 (list "conflicts" "store" from-rev to-rev)
                 :finished (lambda (output error status arguments)
                             (xmtn-dvc-log-clean)

                             :error (lambda (output error status arguments)
                                      (xmtn-dvc-log-clean)
                                      (pop-to-buffer error))))))
          ;; create conflicts buffer
          (save-excursion
            (let ((dvc-switch-to-buffer-first nil))
              (xmtn-conflicts-review default-directory)
              (current-buffer)))))))

(defun xmtn-propagate-conflicts (data)
  "Return value for xmtn-propagate-data-conflicts for DATA."
  ;; if conflicts-buffer is nil, this does the right thing.
  (let ((revs-current
         (and (xmtn-propagate-data-conflicts-buffer data)
              (with-current-buffer (xmtn-propagate-data-conflicts-buffer data)
                (and xmtn-conflicts-left-revision
                     (string= (xmtn-propagate-data-from-rev data) xmtn-conflicts-left-revision)
                     xmtn-conflicts-right-revision
                     (string= (xmtn-propagate-data-to-rev data) xmtn-conflicts-right-revision))))))
    (if revs-current
        (with-current-buffer (xmtn-propagate-data-conflicts-buffer data)
          (xmtn-conflicts-update-counts))
      ;; recreate conflicts
      (if (xmtn-propagate-data-conflicts-buffer data)
          (kill-buffer (xmtn-propagate-data-conflicts-buffer data)))
      (setf (xmtn-propagate-data-conflicts-buffer data)
            (xmtn-propagate-conflicts-buffer
             (xmtn-propagate-from-work data)
             (xmtn-propagate-data-from-rev data)
             (xmtn-propagate-to-work data)
             (xmtn-propagate-data-to-rev data))))

    (with-current-buffer (xmtn-propagate-data-conflicts-buffer data)
      (if (= xmtn-conflicts-total-count xmtn-conflicts-resolved-count)
          'ok
        'need-resolve))))

(defun xmtn-propagate-refresh-one (data)
  "Refresh DATA."
  (let ((from-work (xmtn-propagate-from-work data))
        (to-work (xmtn-propagate-to-work data)))

    (setf (xmtn-propagate-data-from-rev data) (xmtn--get-base-revision-hash-id-or-null from-work))
    (setf (xmtn-propagate-data-to-rev data) (xmtn--get-base-revision-hash-id-or-null to-work))

    (setf (xmtn-propagate-data-propagate-needed data)
          (xmtn-propagate-needed
           from-work
           (xmtn-propagate-data-from-rev data)
           (xmtn-propagate-data-to-rev data)))

    (let ((heads (xmtn--heads from-work nil)))
      (case (length heads)
        (1 (if (string= (xmtn-propagate-data-from-rev data) (nth 0 heads))
               (setf (xmtn-propagate-data-from-heads data) 'at-head)
             (setf (xmtn-propagate-data-from-heads data) 'need-update)))

        (t (setf (xmtn-propagate-data-from-heads data) 'need-merge))))

    (let ((heads (xmtn--heads to-work nil)))
      (case (length heads)
        (1 (if (string= (xmtn-propagate-data-to-rev data) (nth 0 heads))
               (setf (xmtn-propagate-data-to-heads data) 'at-head)
             (setf (xmtn-propagate-data-to-heads data) 'need-update)))

        (t (setf (xmtn-propagate-data-to-heads data) 'need-merge))))

    (if (xmtn-propagate-data-propagate-needed data)
        ;; these checks are slow, so don't do them if they probably are not needed.
        (progn
          (ecase (xmtn-propagate-data-from-local-changes data)
            ((need-scan need-status)
             (setf (xmtn-propagate-data-from-local-changes data) (xmtn-propagate-local-changes from-work)))
            (ok nil))

          (ecase (xmtn-propagate-data-to-local-changes data)
            ((need-scan need-status)
             (setf (xmtn-propagate-data-to-local-changes data) (xmtn-propagate-local-changes to-work)))
            (ok nil))

          (setf (xmtn-propagate-data-conflicts data)
                (xmtn-propagate-conflicts data)))

      ;; propagate not needed
      (ecase (xmtn-propagate-data-from-local-changes data)
        (need-status
         (setf (xmtn-propagate-data-from-local-changes data) 'need-scan))
        ((need-scan ok nil)))

      (ecase (xmtn-propagate-data-to-local-changes data)
        (need-status
         (setf (xmtn-propagate-data-to-local-changes data) 'need-scan))
        ((need-scan ok nil)))

      (setf (xmtn-propagate-data-conflicts data) 'need-scan))

    (setf (xmtn-propagate-data-need-refresh data) nil))

  ;; return non-nil to refresh display as we go along
  t)

(defun xmtn-propagate-refresh ()
  "Refresh status of each ewoc element."
  (interactive)
  (ewoc-map 'xmtn-propagate-refresh-one xmtn-propagate-ewoc)
  (message "done"))

(defun xmtn--filter-non-dir (dir)
  "Return list of all directories in DIR, excluding '.', '..'."
  (let ((default-directory dir)
        (subdirs (directory-files dir)))
    (setq subdirs
          (mapcar (lambda (filename)
                    (if (and (file-directory-p filename)
                             (not (string= "." filename))
                             (not (string= ".." filename)))
                        filename))
                  subdirs))
    (delq nil subdirs)))

;;;###autoload
(defun xmtn-propagate-multiple (from-dir to-dir)
  "Show all actions needed to propagate all projects under FROM-DIR to TO-DIR."
  (interactive "DPropagate all from (root directory): \nDto (root directory): ")
  (let ((from-workspaces (xmtn--filter-non-dir from-dir))
        (to-workspaces (xmtn--filter-non-dir to-dir)))

    (pop-to-buffer (get-buffer-create "*xmtn-propagate*"))
    (xmtn-propagate-mode)
    (setq xmtn-propagate-from-root (file-name-as-directory from-dir))
    (setq xmtn-propagate-to-root (file-name-as-directory to-dir))
    (let ((inhibit-read-only t)) (delete-region (point-min) (point-max)))
    (ewoc-set-hf
     xmtn-propagate-ewoc
     (concat
      (format "From root : %s\n" xmtn-propagate-from-root)
      (format "  To root : %s\n" xmtn-propagate-to-root)
      )
     "")
    (dolist (workspace from-workspaces)
      (if (member workspace to-workspaces)
          (ewoc-enter-last xmtn-propagate-ewoc
                           (make-xmtn-propagate-data
                            :to-work workspace
                            :from-work workspace
                            :need-refresh t))))

    (xmtn-propagate-refresh)
    (xmtn-propagate-next)))

;;;###autoload
(defun xmtn-propagate-one (from-work to-work)
  "Show all actions needed to propagate FROM-WORK to TO-WORK."
  (interactive "DPropagate all from (workspace): \nDto (workspace): ")
  (pop-to-buffer (get-buffer-create "*xmtn-propagate*"))
  (xmtn-propagate-mode)
  (setq xmtn-propagate-from-root (expand-file-name (concat (file-name-as-directory from-work) "../")))
  (setq xmtn-propagate-to-root (expand-file-name (concat (file-name-as-directory to-work) "../")))

  (let ((inhibit-read-only t)) (delete-region (point-min) (point-max)))
  (ewoc-set-hf
   xmtn-propagate-ewoc
   (concat
    (format "From root : %s\n" xmtn-propagate-from-root)
    (format "  To root : %s\n" xmtn-propagate-to-root)
    )
   "")
  (ewoc-enter-last xmtn-propagate-ewoc
                   (make-xmtn-propagate-data
                    :from-work (file-name-nondirectory from-work)
                    :to-work (file-name-nondirectory to-work)
                    :need-refresh t))

  (xmtn-propagate-refresh)
  (xmtn-propagate-next))

(provide 'xmtn-propagate)

;; end of file
