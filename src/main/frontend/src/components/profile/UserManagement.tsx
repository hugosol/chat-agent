import { useState, useEffect } from "react";
import { Modal } from "../../shared/Modal";
import classes from "./UserManagement.module.css";

interface UserItem {
  id: string;
  username: string;
  createTime: string;
  enabled: boolean;
}

function UserManagement(): JSX.Element {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [showCreate, setShowCreate] = useState(false);
  const [newUsername, setNewUsername] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [createError, setCreateError] = useState("");

  const [disableTarget, setDisableTarget] = useState<UserItem | null>(null);

  const [resetTarget, setResetTarget] = useState<UserItem | null>(null);
  const [adminPassword, setAdminPassword] = useState("");
  const [resetNewPassword, setResetNewPassword] = useState("");
  const [resetError, setResetError] = useState("");

  function loadUsers(): void {
    fetch("/api/admin/users")
      .then((r) => r.json())
      .then((data) => setUsers(data));
  }

  useEffect(() => {
    loadUsers();
  }, []);

  function handleCreate(): void {
    setCreateError("");
    if (newPassword.length < 6) {
      setCreateError("Password must be at least 6 characters");
      return;
    }
    fetch("/api/admin/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: newUsername, password: newPassword }),
    })
      .then((r) => {
        if (r.ok) {
          setShowCreate(false);
          setNewUsername("");
          setNewPassword("");
          loadUsers();
        } else {
          setCreateError("Failed to create user. Username may already exist.");
        }
      });
  }

  function handleToggleEnabled(): void {
    if (!disableTarget) return;
    fetch("/api/admin/users/" + disableTarget.id + "/enabled", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled: !disableTarget.enabled }),
    })
      .then((r) => {
        if (r.ok) {
          setDisableTarget(null);
          loadUsers();
        }
      });
  }

  function handleResetPassword(): void {
    setResetError("");
    if (!resetTarget) return;
    if (resetNewPassword.length < 6) {
      setResetError("Password must be at least 6 characters");
      return;
    }
    fetch("/api/admin/users/" + resetTarget.id + "/password", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ adminPassword, newPassword: resetNewPassword }),
    })
      .then((r) => {
        if (r.ok) {
          setResetTarget(null);
          setAdminPassword("");
          setResetNewPassword("");
        } else {
          setResetError("Failed to reset password.");
        }
      });
  }

  return (
    <div className={classes.root}>
      <div className={classes.header}>
        <h2 className={classes.title}>User Management</h2>
        <button
          className={classes.createBtn}
          data-testid="create-user-btn"
          onClick={() => setShowCreate(true)}
        >
          + Create User
        </button>
      </div>

      <table className={classes.table}>
        <thead>
          <tr>
            <th>Username</th>
            <th>Created</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.username}</td>
              <td className={classes.dateCell}>{u.createTime?.substring(0, 10) || "-"}</td>
              <td data-testid={`user-status-${u.id}`}>
                {u.enabled ? "Enabled" : "Disabled"}
              </td>
              <td className={classes.actionsCell}>
                <button
                  className={classes.actionBtn}
                  data-testid={`disable-btn-${u.id}`}
                  onClick={() => setDisableTarget(u)}
                >
                  {u.enabled ? "Disable" : "Enable"}
                </button>
                <button
                  className={classes.actionBtn}
                  data-testid={`reset-password-btn-${u.id}`}
                  onClick={() => setResetTarget(u)}
                >
                  Reset Password
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <Modal
        open={showCreate}
        title="Create User"
        onClose={() => setShowCreate(false)}
        onSave={handleCreate}
        saveLabel="Create"
      >
        <div className={classes.modalBody}>
          <label className={classes.modalLabel}>Username</label>
          <input
            type="text"
            className={classes.modalInput}
            value={newUsername}
            onChange={(e) => setNewUsername(e.target.value)}
            data-testid="create-user-modal"
            autoFocus
          />
          <label className={classes.modalLabel}>Password</label>
          <div className={classes.passwordRow}>
            <input
              type={showPassword ? "text" : "password"}
              className={classes.modalInput}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
            <button
              type="button"
              className={classes.togglePwdBtn}
              onClick={() => setShowPassword(!showPassword)}
              data-testid="toggle-password-btn"
            >
              {showPassword ? "Hide" : "Show"}
            </button>
          </div>
          {createError && <div className={classes.modalError}>{createError}</div>}
        </div>
      </Modal>

      <Modal
        open={disableTarget !== null}
        title={disableTarget ? (disableTarget.enabled ? "Disable User" : "Enable User") : ""}
        onClose={() => setDisableTarget(null)}
        onSave={handleToggleEnabled}
        saveLabel={disableTarget?.enabled ? "Disable" : "Enable"}
        danger={disableTarget?.enabled === true}
      >
        <div className={classes.modalBody} data-testid="confirm-disable-modal">
          {disableTarget && (
            <p>
              Are you sure you want to {disableTarget.enabled ? "disable" : "enable"}{" "}
              user <strong>{disableTarget.username}</strong>?
            </p>
          )}
        </div>
      </Modal>

      <Modal
        open={resetTarget !== null}
        title="Reset Password"
        onClose={() => setResetTarget(null)}
        onSave={handleResetPassword}
        saveLabel="Reset"
      >
        <div className={classes.modalBody} data-testid="reset-password-modal">
          {resetTarget && (
            <>
              <p>Reset password for <strong>{resetTarget.username}</strong></p>
              <label className={classes.modalLabel}>Your Password</label>
              <input
                type="password"
                className={classes.modalInput}
                value={adminPassword}
                onChange={(e) => setAdminPassword(e.target.value)}
              />
              <label className={classes.modalLabel}>New Password</label>
              <input
                type="password"
                className={classes.modalInput}
                value={resetNewPassword}
                onChange={(e) => setResetNewPassword(e.target.value)}
                minLength={6}
              />
              {resetError && <div className={classes.modalError}>{resetError}</div>}
            </>
          )}
        </div>
      </Modal>
    </div>
  );
}

export { UserManagement };
export type { UserItem };
