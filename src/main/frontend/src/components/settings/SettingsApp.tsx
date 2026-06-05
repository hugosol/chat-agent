import { Header } from "../Header/Header";
import { SettingsPage } from "./SettingsPage";

function SettingsApp(): JSX.Element {
  return (
    <div>
      <Header />
      <SettingsPage />
    </div>
  );
}

export { SettingsApp };
