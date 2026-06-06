import { useState, useEffect } from "react";
import { Header } from "../Header/Header";
import { UserManagement } from "./UserManagement";
import { PasswordChangeForm } from "./PasswordChangeForm";

interface UserMe {
  username: string;
  admin: boolean;
}

function ProfileApp(): JSX.Element {
  const [user, setUser] = useState<UserMe | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    fetch("/api/user/me")
      .then((r) => r.json())
      .then((data) => {
        setUser(data);
        setLoading(false);
      })
      .catch(() => {
        setError(true);
        setLoading(false);
      });
  }, []);

  if (loading) {
    return (
      <div>
        <Header />
        <div className="loading" data-testid="profile-loading">
          Loading...
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <Header />
        <div className="error" data-testid="profile-error">
          Failed to load profile. Please try again.
        </div>
      </div>
    );
  }

  return (
    <div>
      <Header />
      {user?.admin ? (
        <>
          <div data-testid="user-management-title">
            <UserManagement />
          </div>
          <PasswordChangeForm />
        </>
      ) : (
        <PasswordChangeForm />
      )}
    </div>
  );
}

export { ProfileApp };
