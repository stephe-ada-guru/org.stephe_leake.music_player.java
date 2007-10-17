;;; xgit-dvc.el --- The dvc layer for git

;; Copyright (C) 2006-2007 by all contributors

;; Author: Stefan Reichoer, <stefan@xsteve.at>

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

;; This file provides the common dvc layer for git


;;; History:

;;

;;; Code:

(require 'xgit)
(eval-and-compile (require 'dvc-unified))

;;;###autoload
(dvc-register-dvc 'xgit "git")

;;;###autoload
(defalias 'xgit-dvc-tree-root 'xgit-tree-root)

;;;###autoload
(defun xgit-dvc-status (&optional path)
  (xgit-status))

(defalias 'xgit-dvc-add-files 'xgit-add-files)
(defalias 'xgit-dvc-remove-files 'xgit-remove-files)
(defalias 'xgit-dvc-revert-files 'xgit-revert-files)

;;;###autoload
(defalias 'xgit-dvc-command-version 'xgit-command-version)

(defalias 'xgit-dvc-diff 'xgit-diff)

(defalias 'xgit-dvc-delta 'xgit-delta)

(defun xgit-dvc-log-edit-file-name-func ()
  (concat (xgit-tree-root) xgit-log-edit-file-name))

(defun xgit-dvc-log-edit-done ()
  "Finish a commit for git, using git commit -a"
  (let ((buffer (find-file-noselect (dvc-log-edit-file-name)))
        (files-to-commit (when (buffer-live-p dvc-partner-buffer)
                           (with-current-buffer dvc-partner-buffer
                             (dvc-current-file-list 'nil-if-none-marked)))))
    (dvc-log-flush-commit-file-list)
    (save-buffer buffer)
    (message "committing %S in %s" (or files-to-commit "all files") (dvc-tree-root))
    (dvc-run-dvc-sync
     'xgit (append (list "commit"
                         (unless (xgit-use-index-p) "-a")
                         "-F" (dvc-log-edit-file-name))
                   files-to-commit)
     :finished (dvc-capturing-lambda
                   (output error status arguments)
                 (dvc-show-error-buffer output 'commit)
                 (let ((inhibit-read-only t))
                   (goto-char (point-max))
                   (insert (with-current-buffer error
                             (buffer-string))))
                 (dvc-log-close (capture buffer))
                 ;; doesn't work at the moment (Stefan, 10.02.2006)
                 ;; (dvc-diff-clear-buffers 'xgit (capture default-directory)
                 ;;  "* Just committed! Please refresh buffer\n")
                 (message "git commit finished")))
    (dvc-tips-popup-maybe)))

;;;###autoload
(defun xgit-dvc-log (arg last-n)
  "Shows the changelog in the current git tree.
ARG is passed as prefix argument"
  (call-interactively 'xgit-log))

(defalias 'xgit-dvc-revlog-get-revision 'xgit-revlog-get-revision)

(defalias 'xgit-dvc-name-construct 'xgit-name-construct)

(defun xgit-dvc-changelog (arg)
  "Shows the changelog in the current git tree.
ARG is passed as prefix argument"
  (call-interactively 'xgit-log))

(defalias 'xgit-dvc-prepare-environment 'xgit-prepare-environment)

(defalias 'xgit-dvc-revision-get-last-revision 'xgit-revision-get-last-revision)

(defalias 'xgit-dvc-last-revision 'xgit-last-revision)

(provide 'xgit-dvc)
;;; xgit-dvc.el ends here
