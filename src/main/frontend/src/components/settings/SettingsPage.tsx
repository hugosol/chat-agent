import { useState, useEffect, useCallback } from "react";
import { showToast } from "../../shared/Toast";
import styles from "./SettingsPage.module.css";

interface SettingsFields {
  newCardDailyLimit: string;
  dayStartHour: string;
  timezone: string;
  learningSteps: string;
  relearningSteps: string;
  desiredRetention: string;
  maximumInterval: string;
  enableFuzz: boolean;
  shuffleDueCards: boolean;
}

const DEFAULTS: SettingsFields = {
  newCardDailyLimit: "20",
  dayStartHour: "6",
  timezone: "",
  learningSteps: "1m,10m",
  relearningSteps: "10m",
  desiredRetention: "0.9",
  maximumInterval: "36500",
  enableFuzz: true,
  shuffleDueCards: false,
};

const LEARNING_PRESETS = [
  { label: "Anki 默认", value: "1m,10m", desc: "两步验证：1分钟后快速确认，10分钟后再检查一次，确保真正记住后才进入长期复习。最保守的选项，Anki 默认设置。" },
  { label: "快速毕业", value: "30m", desc: "一步验证：30分钟后检查一次即可毕业。适合对自己记忆力有信心、不想被频繁打断的 Learner。" },
  { label: "只一步", value: "10m", desc: "一步验证：10分钟后检查一次即可毕业。比快速毕业更短的第一步检查，适合想快但又不放心跳过的 Learner。" },
  { label: "无步骤", value: "", desc: "跳过短期测试，新卡评 Good 后直接进入长期复习。相信 FSRS 的首次间隔计算，不需要反复确认。" },
];

const RELEARNING_PRESETS = [
  { label: "Anki 默认", value: "10m", desc: "遗忘后进入重学流程：10分钟后重新出现。给你一次快速重学机会，防止刚忘就被推到几天后。" },
  { label: "不留重学步", value: "", desc: "遗忘后不进入单独的重学流程。FSRS 自动计算一个更短的间隔，卡片留在 Review 状态直接进入下一次复习。" },
];

const CUSTOM_LEARNING_HINT = "逗号分隔，单位 s/m/h/d，留空=跳过短期测试";
const CUSTOM_RELEARNING_HINT = "逗号分隔，单位 s/m/h/d，留空=遗忘后不进入重学流程";

const STEPS_REGEX = /^(\d+[smhd],)*\d+[smhd]$/;

function detectPreset(value: string, presets: typeof LEARNING_PRESETS): string {
  const match = presets.find((p) => p.value === value.trim());
  return match ? match.label : "自定义";
}

function SettingsPage(): JSX.Element {
  const [fields, setFields] = useState<SettingsFields>({ ...DEFAULTS });
  const [learningPreset, setLearningPreset] = useState("Anki 默认");
  const [relearningPreset, setRelearningPreset] = useState("Anki 默认");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    loadPreferences();
  }, []);

  const loadPreferences = async () => {
    try {
      const res = await fetch("/api/user/preferences", { credentials: "same-origin" });
      if (res.ok) {
        const prefs = await res.json();
        const loaded: SettingsFields = {
          newCardDailyLimit: String(prefs.newCardDailyLimit ?? DEFAULTS.newCardDailyLimit),
          dayStartHour: String(prefs.dayStartHour ?? DEFAULTS.dayStartHour),
          timezone: prefs.timezone ?? "",
          learningSteps: prefs.learningSteps ?? DEFAULTS.learningSteps,
          relearningSteps: prefs.relearningSteps ?? DEFAULTS.relearningSteps,
          desiredRetention: prefs.desiredRetention != null ? String(prefs.desiredRetention) : DEFAULTS.desiredRetention,
          maximumInterval: prefs.maximumInterval != null ? String(prefs.maximumInterval) : DEFAULTS.maximumInterval,
          enableFuzz: prefs.enableFuzz ?? DEFAULTS.enableFuzz,
          shuffleDueCards: prefs.shuffleDueCards ?? DEFAULTS.shuffleDueCards,
        };
        setFields(loaded);
        setLearningPreset(detectPreset(loaded.learningSteps, LEARNING_PRESETS));
        setRelearningPreset(detectPreset(loaded.relearningSteps, RELEARNING_PRESETS));
      }
    } catch {
      // use defaults
    } finally {
      setLoaded(true);
    }
  };

  const updateField = useCallback((key: keyof SettingsFields, value: string | boolean) => {
    setFields((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
  }, []);

  const handleLearningPresetChange = (presetLabel: string) => {
    setLearningPreset(presetLabel);
    const preset = LEARNING_PRESETS.find((p) => p.label === presetLabel);
    if (preset) {
      updateField("learningSteps", preset.value);
    }
  };

  const handleRelearningPresetChange = (presetLabel: string) => {
    setRelearningPreset(presetLabel);
    const preset = RELEARNING_PRESETS.find((p) => p.label === presetLabel);
    if (preset) {
      updateField("relearningSteps", preset.value);
    }
  };

  const handleLearningStepsInput = (value: string) => {
    updateField("learningSteps", value);
    const detected = detectPreset(value, LEARNING_PRESETS);
    setLearningPreset(detected);
  };

  const handleRelearningStepsInput = (value: string) => {
    updateField("relearningSteps", value);
    const detected = detectPreset(value, RELEARNING_PRESETS);
    setRelearningPreset(detected);
  };

  const validate = (): boolean => {
    const errs: Record<string, string> = {};

    const dailyLimit = parseInt(fields.newCardDailyLimit, 10);
    if (isNaN(dailyLimit) || dailyLimit < 0 || !/^\d+$/.test(fields.newCardDailyLimit)) {
      errs.newCardDailyLimit = "请输入大于等于 0 的整数";
    }

    const dayHour = parseInt(fields.dayStartHour, 10);
    if (isNaN(dayHour) || dayHour < 0 || dayHour > 23 || !/^\d+$/.test(fields.dayStartHour)) {
      errs.dayStartHour = "请输入 0 到 23 之间的整数";
    }

    if (fields.learningSteps && !STEPS_REGEX.test(fields.learningSteps)) {
      errs.learningSteps = "格式错误。如: 1m,10m 或 30s 或留空";
    }

    if (fields.relearningSteps && !STEPS_REGEX.test(fields.relearningSteps)) {
      errs.relearningSteps = "格式错误。如: 1m,10m 或 30s 或留空";
    }

    if (fields.desiredRetention) {
      const dr = parseFloat(fields.desiredRetention);
      if (isNaN(dr) || dr < 0.01 || dr > 0.99) {
        errs.desiredRetention = "请输入 0.01 到 0.99 之间的数值";
      }
    }

    if (fields.maximumInterval) {
      const mi = parseInt(fields.maximumInterval, 10);
      if (isNaN(mi) || mi < 1) {
        errs.maximumInterval = "请输入大于等于 1 的整数";
      }
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      const body: Record<string, unknown> = {
        newCardDailyLimit: parseInt(fields.newCardDailyLimit, 10),
        dayStartHour: parseInt(fields.dayStartHour, 10),
        timezone: fields.timezone || null,
        learningSteps: fields.learningSteps || null,
        relearningSteps: fields.relearningSteps || null,
        desiredRetention: fields.desiredRetention ? parseFloat(fields.desiredRetention) : null,
        maximumInterval: fields.maximumInterval ? parseInt(fields.maximumInterval, 10) : null,
        enableFuzz: fields.enableFuzz,
        shuffleDueCards: fields.shuffleDueCards,
      };
      const res = await fetch("/api/user/preferences", {
        method: "PUT",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (res.ok) {
        showToast("设置已保存");
      } else {
        const data = await res.json();
        if (data.errors) {
          setErrors(data.errors);
        } else {
          showToast("保存失败");
        }
      }
    } catch {
      showToast("保存失败");
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    setFields({ ...DEFAULTS });
    setLearningPreset("Anki 默认");
    setRelearningPreset("Anki 默认");
    setErrors({});
  };

  const isLearningCustom = learningPreset === "自定义";
  const isRelearningCustom = relearningPreset === "自定义";

  const learningDesc = isLearningCustom
    ? CUSTOM_LEARNING_HINT
    : LEARNING_PRESETS.find((p) => p.label === learningPreset)?.desc ?? "";
  const relearningDesc = isRelearningCustom
    ? CUSTOM_RELEARNING_HINT
    : RELEARNING_PRESETS.find((p) => p.label === relearningPreset)?.desc ?? "";

  if (!loaded) {
    return <div className={styles.container}>Loading...</div>;
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>设置</h1>

      {/* Group 1: Daily Review Settings */}
      <div className={styles.group}>
        <h2 className={styles.groupTitle}>每日复习设置</h2>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="newCardDailyLimit">每日新卡上限</label>
          <div className={styles.inputRow}>
            <input
              id="newCardDailyLimit"
              data-testid="settings-newCardDailyLimit"
              className={`${styles.input} ${styles.inputNarrow} ${errors.newCardDailyLimit ? styles.inputError : ""}`}
              type="text"
              inputMode="numeric"
              value={fields.newCardDailyLimit}
              onChange={(e) => updateField("newCardDailyLimit", e.target.value)}
            />
            <span className={styles.suffix}>张</span>
          </div>
          {errors.newCardDailyLimit && <p className={styles.error}>{errors.newCardDailyLimit}</p>}
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="dayStartHour">每日起始时间</label>
          <div className={styles.inputRow}>
            <input
              id="dayStartHour"
              data-testid="settings-dayStartHour"
              className={`${styles.input} ${styles.inputNarrow} ${errors.dayStartHour ? styles.inputError : ""}`}
              type="text"
              inputMode="numeric"
              value={fields.dayStartHour}
              onChange={(e) => updateField("dayStartHour", e.target.value)}
            />
            <span className={styles.suffix}>时</span>
          </div>
          {errors.dayStartHour && <p className={styles.error}>{errors.dayStartHour}</p>}
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="timezone">时区</label>
          <input
            id="timezone"
            data-testid="settings-timezone"
            className={styles.input}
            type="text"
            placeholder="Asia/Shanghai"
            value={fields.timezone}
            onChange={(e) => updateField("timezone", e.target.value)}
          />
        </div>
      </div>

      {/* Group 2: Learning & Relearning Steps */}
      <div className={styles.group}>
        <h2 className={styles.groupTitle}>学习步 & 重学步</h2>

        <div className={styles.field}>
          <label className={styles.label}>学习步</label>
          <select
            data-testid="settings-learningPreset"
            className={styles.select}
            value={learningPreset}
            onChange={(e) => handleLearningPresetChange(e.target.value)}
          >
            {LEARNING_PRESETS.map((p) => (
              <option key={p.label} value={p.label}>{p.label}</option>
            ))}
            <option value="自定义">自定义</option>
          </select>
          <input
            data-testid="settings-learningSteps"
            className={`${styles.input} ${styles.stepsInput} ${errors.learningSteps ? styles.inputError : ""}`}
            type="text"
            value={fields.learningSteps}
            readOnly={!isLearningCustom}
            disabled={!isLearningCustom}
            onChange={(e) => handleLearningStepsInput(e.target.value)}
          />
          <p className={styles.hint}>{learningDesc}</p>
          {errors.learningSteps && <p className={styles.error}>{errors.learningSteps}</p>}
        </div>

        <div className={styles.field}>
          <label className={styles.label}>重学步</label>
          <select
            data-testid="settings-relearningPreset"
            className={styles.select}
            value={relearningPreset}
            onChange={(e) => handleRelearningPresetChange(e.target.value)}
          >
            {RELEARNING_PRESETS.map((p) => (
              <option key={p.label} value={p.label}>{p.label}</option>
            ))}
            <option value="自定义">自定义</option>
          </select>
          <input
            data-testid="settings-relearningSteps"
            className={`${styles.input} ${styles.stepsInput} ${errors.relearningSteps ? styles.inputError : ""}`}
            type="text"
            value={fields.relearningSteps}
            readOnly={!isRelearningCustom}
            disabled={!isRelearningCustom}
            onChange={(e) => handleRelearningStepsInput(e.target.value)}
          />
          <p className={styles.hint}>{relearningDesc}</p>
          {errors.relearningSteps && <p className={styles.error}>{errors.relearningSteps}</p>}
        </div>
      </div>

      {/* Group 3: FSRS Parameters */}
      <div className={styles.group}>
        <h2 className={styles.groupTitle}>FSRS 参数</h2>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="desiredRetention">目标正确率</label>
          <div className={styles.inputRow}>
            <input
              id="desiredRetention"
              data-testid="settings-desiredRetention"
              className={`${styles.input} ${styles.inputNarrow} ${errors.desiredRetention ? styles.inputError : ""}`}
              type="text"
              inputMode="decimal"
              value={fields.desiredRetention}
              onChange={(e) => updateField("desiredRetention", e.target.value)}
            />
            <span className={styles.suffix}>(0.01~0.99)</span>
          </div>
          {errors.desiredRetention && <p className={styles.error}>{errors.desiredRetention}</p>}
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="maximumInterval">最大间隔</label>
          <div className={styles.inputRow}>
            <input
              id="maximumInterval"
              data-testid="settings-maximumInterval"
              className={`${styles.input} ${styles.inputNarrow} ${errors.maximumInterval ? styles.inputError : ""}`}
              type="text"
              inputMode="numeric"
              value={fields.maximumInterval}
              onChange={(e) => updateField("maximumInterval", e.target.value)}
            />
            <span className={styles.suffix}>天</span>
          </div>
          {errors.maximumInterval && <p className={styles.error}>{errors.maximumInterval}</p>}
        </div>

        <div className={styles.field}>
          <label className={styles.label}>随机微调 (Fuzz)</label>
          <div className={styles.toggleRow}>
            <label className={styles.toggle}>
              <input
                type="checkbox"
                data-testid="settings-enableFuzz"
                checked={fields.enableFuzz}
                onChange={(e) => updateField("enableFuzz", e.target.checked)}
              />
              <span className={styles.toggleSlider} />
            </label>
            <span className={styles.toggleHint}>开启后卡片到期日有小幅随机扰动，避免扎堆</span>
          </div>
        </div>

        <div className={styles.field}>
          <label className={styles.label}>洗牌 (Shuffle)</label>
          <div className={styles.toggleRow}>
            <label className={styles.toggle}>
              <input
                type="checkbox"
                data-testid="settings-shuffleDueCards"
                checked={fields.shuffleDueCards}
                onChange={(e) => updateField("shuffleDueCards", e.target.checked)}
              />
              <span className={styles.toggleSlider} />
            </label>
            <span className={styles.toggleHint}>开启后同时到期的卡片随机出现</span>
          </div>
        </div>
      </div>

      {/* Bottom Buttons */}
      <div className={styles.buttons}>
        <button
          type="button"
          data-testid="settings-resetBtn"
          className={styles.btnReset}
          onClick={handleReset}
        >
          恢复默认值
        </button>
        <button
          type="button"
          data-testid="settings-saveBtn"
          className={styles.btnSave}
          disabled={saving}
          onClick={handleSave}
        >
          {saving ? "保存中..." : "保存设置"}
        </button>
      </div>
    </div>
  );
}

export { SettingsPage };
export type { SettingsFields };
