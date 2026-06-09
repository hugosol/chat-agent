import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { SettingsPage } from "../../components/settings/SettingsPage";

const mockPrefs = {
  newCardDailyLimit: 20,
  dayStartHour: 6,
  utcOffset: 8,
  lastDeckId: "deck-1",
  lastMode: "STANDARD",
  learningSteps: "1m,10m",
  relearningSteps: "10m",
  desiredRetention: 0.9,
  maximumInterval: 36500,
  enableFuzz: true,
  shuffleDueCards: false,
};

function setupFetch(prefs = mockPrefs) {
  (global as unknown as { fetch: typeof fetch }).fetch = vi.fn((url: string, init?: RequestInit) => {
    if (url === "/api/user/preferences" && (!init || init.method === undefined)) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(prefs),
      } as Response);
    }
    if (url === "/api/user/preferences" && init?.method === "PUT") {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ ...prefs, ...JSON.parse(init.body as string) }),
      } as Response);
    }
    return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as Response);
  });
}

describe("SettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupFetch();
  });

  it("renders all 9 fields on load", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-newCardDailyLimit")).toBeTruthy();
    });

    expect(screen.getByTestId("settings-dayStartHour")).toBeTruthy();
    expect(screen.getByTestId("settings-utcOffset")).toBeTruthy();
    expect(screen.getByTestId("settings-learningPreset")).toBeTruthy();
    expect(screen.getByTestId("settings-learningSteps")).toBeTruthy();
    expect(screen.getByTestId("settings-relearningPreset")).toBeTruthy();
    expect(screen.getByTestId("settings-relearningSteps")).toBeTruthy();
    expect(screen.getByTestId("settings-desiredRetention")).toBeTruthy();
    expect(screen.getByTestId("settings-maximumInterval")).toBeTruthy();
    expect(screen.getByTestId("settings-enableFuzz")).toBeTruthy();
    expect(screen.getByTestId("settings-shuffleDueCards")).toBeTruthy();
    expect(screen.getByTestId("settings-saveBtn")).toBeTruthy();
    expect(screen.getByTestId("settings-resetBtn")).toBeTruthy();
  });

  it("preset_learning_anki_default", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-learningPreset")).toBeTruthy();
    });

    const presetSelect = screen.getByTestId("settings-learningPreset") as HTMLSelectElement;
    expect(presetSelect.value).toBe("Anki 默认");

    const stepsInput = screen.getByTestId("settings-learningSteps") as HTMLInputElement;
    expect(stepsInput.value).toBe("1m,10m");
    expect(stepsInput.readOnly).toBe(true);

    expect(screen.getByText(/两步验证：1分钟后快速确认/)).toBeTruthy();
  });

  it("preset_learning_fast_graduation", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-learningPreset")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("settings-learningPreset"), {
      target: { value: "快速毕业" },
    });

    const stepsInput = screen.getByTestId("settings-learningSteps") as HTMLInputElement;
    expect(stepsInput.value).toBe("30m");
    expect(stepsInput.readOnly).toBe(true);

    expect(screen.getByText(/一步验证：30分钟后检查一次即可毕业/)).toBeTruthy();
  });

  it("preset_learning_custom_enables_editing", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-learningPreset")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("settings-learningPreset"), {
      target: { value: "自定义" },
    });

    const stepsInput = screen.getByTestId("settings-learningSteps") as HTMLInputElement;
    expect(stepsInput.readOnly).toBe(false);
    expect(stepsInput.disabled).toBe(false);

    expect(screen.getByText(/逗号分隔，单位 s\/m\/h\/d，留空=跳过短期测试/)).toBeTruthy();
  });

  it("preset_learning_auto_detect", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-learningPreset")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("settings-learningPreset"), {
      target: { value: "自定义" },
    });

    fireEvent.change(screen.getByTestId("settings-learningSteps"), {
      target: { value: "30m" },
    });

    const presetSelect = screen.getByTestId("settings-learningPreset") as HTMLSelectElement;
    expect(presetSelect.value).toBe("快速毕业");
  });

  it("preset_relearning_anki_default", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-relearningPreset")).toBeTruthy();
    });

    const presetSelect = screen.getByTestId("settings-relearningPreset") as HTMLSelectElement;
    expect(presetSelect.value).toBe("Anki 默认");

    const stepsInput = screen.getByTestId("settings-relearningSteps") as HTMLInputElement;
    expect(stepsInput.value).toBe("10m");
    expect(stepsInput.readOnly).toBe(true);

    expect(screen.getByText(/遗忘后进入重学流程：10分钟后重新出现/)).toBeTruthy();
  });

  it("preset_relearning_no_steps", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-relearningPreset")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("settings-relearningPreset"), {
      target: { value: "不留重学步" },
    });

    const stepsInput = screen.getByTestId("settings-relearningSteps") as HTMLInputElement;
    expect(stepsInput.value).toBe("");

    expect(screen.getByText(/遗忘后不进入单独的重学流程/)).toBeTruthy();
  });

  it("validation_desired_retention_out_of_range", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-desiredRetention")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("settings-desiredRetention"), {
      target: { value: "1.5" },
    });

    fireEvent.click(screen.getByTestId("settings-saveBtn"));

    await waitFor(() => {
      expect(screen.getByText(/请输入 0.01 到 0.99 之间的数值/)).toBeTruthy();
    });
  });

  it("validation_learning_steps_bad_format", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-learningPreset")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("settings-learningPreset"), {
      target: { value: "自定义" },
    });

    fireEvent.change(screen.getByTestId("settings-learningSteps"), {
      target: { value: "abc" },
    });

    fireEvent.click(screen.getByTestId("settings-saveBtn"));

    await waitFor(() => {
      expect(screen.getByText(/格式错误/)).toBeTruthy();
    });
  });

  it("save_calls_put_api_with_all_fields", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-newCardDailyLimit")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("settings-newCardDailyLimit"), {
      target: { value: "30" },
    });
    fireEvent.change(screen.getByTestId("settings-desiredRetention"), {
      target: { value: "0.85" },
    });

    fireEvent.click(screen.getByTestId("settings-saveBtn"));

    await waitFor(() => {
      const fetchCalls = (global as unknown as { fetch: ReturnType<typeof vi.fn> }).fetch.mock.calls;
      const putCall = fetchCalls.find((c: unknown[]) =>
        (c as [string, RequestInit?])[1]?.method === "PUT"
      ) as [string, RequestInit] | undefined;
      expect(putCall).toBeTruthy();
      const body = JSON.parse(putCall![1]!.body as string);
      expect(body.newCardDailyLimit).toBe(30);
      expect(body.desiredRetention).toBe(0.85);
      expect(body.learningSteps).toBe("1m,10m");
      expect(body.dayStartHour).toBe(6);
      expect(body.enableFuzz).toBe(true);
      expect(body.shuffleDueCards).toBe(false);
      expect(body.maximumInterval).toBe(36500);
      expect(body.utcOffset).toBe(8);
    });
  });

  it("reset_defaults_button", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-newCardDailyLimit")).toBeTruthy();
    });

    fireEvent.change(screen.getByTestId("settings-newCardDailyLimit"), {
      target: { value: "50" },
    });

    fireEvent.click(screen.getByTestId("settings-resetBtn"));

    const input = screen.getByTestId("settings-newCardDailyLimit") as HTMLInputElement;
    expect(input.value).toBe("20");
  });

  it("toast_after_save_success", async () => {
    render(<SettingsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("settings-saveBtn")).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId("settings-saveBtn"));

    await waitFor(() => {
      expect(screen.getByTestId("toast")).toBeTruthy();
      expect(screen.getByTestId("toast").textContent).toBe("设置已保存");
    });
  });

  it("utcOffset_plus_button_increments", async () => {
    setupFetch();
    render(<SettingsPage />);
    await waitFor(() => {
      expect(screen.getByTestId("settings-utcOffset")).toBeTruthy();
    });

    const plusBtn = screen.getByTestId("settings-utcOffset-plus");
    fireEvent.click(plusBtn);

    const input = screen.getByTestId("settings-utcOffset") as HTMLInputElement;
    expect(input.value).toBe("9");
  });

  it("utcOffset_minus_button_decrements", async () => {
    setupFetch();
    render(<SettingsPage />);
    await waitFor(() => {
      expect(screen.getByTestId("settings-utcOffset")).toBeTruthy();
    });

    const minusBtn = screen.getByTestId("settings-utcOffset-minus");
    fireEvent.click(minusBtn);

    const input = screen.getByTestId("settings-utcOffset") as HTMLInputElement;
    expect(input.value).toBe("7");
  });

  it("utcOffset_plus_capped_at_14", async () => {
    setupFetch({ ...mockPrefs, utcOffset: 14 });
    render(<SettingsPage />);
    await waitFor(() => {
      expect(screen.getByTestId("settings-utcOffset")).toBeTruthy();
    });

    const plusBtn = screen.getByTestId("settings-utcOffset-plus");
    fireEvent.click(plusBtn);

    const input = screen.getByTestId("settings-utcOffset") as HTMLInputElement;
    expect(input.value).toBe("14");
  });

  it("utcOffset_minus_capped_at_neg12", async () => {
    setupFetch({ ...mockPrefs, utcOffset: -12 });
    render(<SettingsPage />);
    await waitFor(() => {
      expect(screen.getByTestId("settings-utcOffset")).toBeTruthy();
    });

    const minusBtn = screen.getByTestId("settings-utcOffset-minus");
    fireEvent.click(minusBtn);

    const input = screen.getByTestId("settings-utcOffset") as HTMLInputElement;
    expect(input.value).toBe("-12");
  });
});
