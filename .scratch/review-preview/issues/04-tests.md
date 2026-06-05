# 04: 测试补充

**Status:** `ready-for-agent`

## 范围

为 preview 功能补充单元测试和集成测试，确保 E2E 兼容性。

## 实现内容

### FsrsSchedulerTest 追加（4 个新测试）
- `preview_returnsFourOutcomes`：验证 Map 包含 AGAIN/HARD/GOOD/EASY 四个键，值非 null
- `preview_differentRatingsProduceDifferentDue`：四种评分的 due 各不相同（至少 Again 和 Easy 的间隔显著不同）
- `preview_noFuzzIsDeterministic`：同一 CardState 调用两次 → `Arrays.equals(before.due(), after.due())` 对每个评分成立
- `preview_newCard_learningStateGraduation`：state=0 新卡 → preview 中 Good/Easy 导致 state=Review，Again 导致 state=Learning,step=0

### ReviewServiceTest 追加（2 个新测试）
- `previewCard_newCard_returnsFourOutcomes`：mock Card、mock FsrsParametersRepository、mock UserPreferencesService → 调用 previewCard → 返回 4 个结果
- `previewCard_reviewedCard_differentIntervals`：已有复习记录的卡片 → 四个评分产出的 due 互不相同

### ReviewControllerTest 扩展（追加断言）
- `startReview_includesPreviewField`：验证响应 JSON 中 `preview` 字段存在，值为包含 4 个 key 的对象
- `nextReview_includesPreviewField`：验证 POST /next 响应中 card 非 null 时含 `preview`

### E2E（ReviewIT）
- 现有 9 个场景：**确认全部通过**（data-testid 不变）
- 可选：追加一个断言验证评分按钮上存在间隔文字（如 `page.locator("[data-testid='rating-good']").textContent()` 包含间隔信息）
- 建议：此 issue 中只确认兼容性，新 E2E 断言在后续迭代中补充

## 依赖
- Issue 01（preview 方法可用）
- Issue 02（API 响应格式确定）
- Issue 03（前端按钮格式确定）

## 验证
- `mvn test` 全部通过
- `mvn verify` E2E 全部通过
