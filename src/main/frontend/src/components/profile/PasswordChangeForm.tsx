import { useState, FormEvent } from "react";
import classes from "./PasswordChangeForm.module.css";

function PasswordChangeForm(): JSX.Element {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);

  function handleSubmit(e: FormEvent): void {
    e.preventDefault();
    setError("");
    setSuccess(false);

    if (newPassword.length < 6) {
      setError("Password must be at least 6 characters");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    fetch("/api/user/password", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ currentPassword, newPassword }),
    })
      .then((r) => {
        if (r.ok) {
          setSuccess(true);
          setCurrentPassword("");
          setNewPassword("");
          setConfirmPassword("");
        } else {
          setError("Failed to change password. Please check your current password.");
        }
      })
      .catch(() => {
        setError("Network error. Please try again.");
      });
  }

  return (
    <form className={classes.form} onSubmit={handleSubmit}>
      <h2 className={classes.title}>Change Password</h2>

      <div className={classes.field}>
        <label className={classes.label}>Current Password</label>
        <input
          type="password"
          className={classes.input}
          data-testid="current-password-input"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          required
        />
      </div>

      <div className={classes.field}>
        <label className={classes.label}>New Password</label>
        <input
          type="password"
          className={classes.input}
          data-testid="new-password-input"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
          minLength={6}
        />
      </div>

      <div className={classes.field}>
        <label className={classes.label}>Confirm New Password</label>
        <input
          type="password"
          className={classes.input}
          data-testid="confirm-password-input"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          required
        />
      </div>

      {error && (
        <div className={classes.error} data-testid="password-error">
          {error}
        </div>
      )}

      {success && (
        <div className={classes.success} data-testid="password-success">
          Password changed successfully.
        </div>
      )}

      <button type="submit" className={classes.btn} data-testid="change-password-btn">
        Change Password
      </button>
    </form>
  );
}

export { PasswordChangeForm };
