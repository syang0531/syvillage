# 자바 변경 안내 — Placitum 블록 3종 텍스처/모델

> **적용 완료** (2026-09-18, 커밋 `347a3ef`). "필수" 절은 `block/FacingTable.java`로 들어갔다. "참고 · 수호상 2칸" 절은 채택하지 않았고, 나중을 위한 메모로만 남긴다.

확정: **탁자 두 종은 `facing` 추가**, 수호상은 1칸 유지. 클로드 코드에 이 파일을 그대로 넘겨도 되도록 썼다.

## 필요한 변경 요약

| 블록 | 모델 | 자바 |
|---|---|---|
| `village_head_table` | `minecraft:block/cube`, 온전한 1칸, 북면 `_front` | **FACING 속성 + 배치/회전 로직** (아래) |
| `lords_table` | 같음 | 같음 |
| `guardian_statue` | 기존 7요소 모델 유지, 텍스처와 UV만 교체 | 없음 (`.noOcclusion()` 이미 있음) |

파티클 텍스처가 바닐라 참조에서 `placitum:block/*_side` / `guardian_statue_plinth`로 바뀐다. 코드 영향 없음.
리소스 파일을 넣고 자바를 아직 안 고치면 `facing=north` 등 변형을 못 찾아 탁자가 보라·검정 오류 모델로 뜬다 — 리소스와 자바를 같은 커밋에 넣을 것.

---

## 필수 · 탁자에 `facing` 추가 (리소스는 `out/src/`에 이미 반영됨)

두 탁자에 공통. POI 등록은 모든 상태를 잡고 있으므로 건드리지 않는다.

1. 블록 클래스(현재 `Block` 직접 사용이면 서브클래스 신설, 예: `block/FacingTable.java`)
   - `public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;`
   - 생성자에서 `registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))`
   - `createBlockStateDefinition(builder)` → `builder.add(FACING)`
   - `getStateForPlacement(ctx)` → `defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite())`
     (정면이 플레이어를 향한다. 모델의 `_front` 텍스처는 north 면에 있다.)
   - `rotate(state, rotation)` → `state.setValue(FACING, rotation.rotate(state.getValue(FACING)))`
   - `mirror(state, mirror)` → `state.rotate(mirror.getRotation(state.getValue(FACING)))`
2. `registry/ModBlocks.java` — 두 탁자의 등록을 새 클래스로 바꾼다. Properties는 그대로.
3. 리소스 — `out/src/…/blockstates/*.json`, `models/block/*.json`에 이미 반영. items/는 변경 없음.
4. 기존 세이브: 속성이 늘어도 기본값 NORTH로 로드된다. 마이그레이션 불필요.

## 참고 · 수호상 2칸 (`out/options/statue_2block/`, 이번엔 채택 안 함)

나중에 올리고 싶을 때를 위한 메모. 문(DoorBlock)과 같은 패턴, 작업량이 상당하다.

1. `GuardianStatue`
   - `public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;` 기본값 LOWER
   - `createBlockStateDefinition` → `builder.add(HALF)`
   - `getStateForPlacement(ctx)` → 위 칸(`pos.above()`)이 `canBeReplaced`일 때만 상태 반환, 아니면 `null`
   - `setPlacedBy(level, pos, state, placer, stack)` → `level.setBlock(pos.above(), state.setValue(HALF, UPPER), 3)`
   - `updateShape(...)` → 반대편 반쪽이 사라졌으면 `Blocks.AIR.defaultBlockState()` 반환 (DoorBlock 참고)
   - `playerWillDestroy(...)` → 반대편 반쪽도 제거 (`DoublePlantBlock.preventDropFromBottomPart` 참고, 위/아래 둘 다)
   - `newBlockEntity(pos, state)` → `state.getValue(HALF) == LOWER`일 때만 `GuardianBlockEntity` 생성, UPPER는 `null`
   - `getTicker` → LOWER만
   - `getRenderShape` → 그대로 MODEL
2. `registry/ModBlocks.java` — BlockEntityType의 valid blocks는 그대로 (같은 Block 인스턴스)
3. 루트테이블 `data/placitum/loot_table/blocks/guardian_statue.json` — `HALF == LOWER` 조건 추가 (안 하면 2개 드롭)
4. POI — 수호상은 직업 블록이 아니므로 무관
5. 리소스 — `out/options/statue_2block/`의 blockstates / models / items로 덮어쓰기
6. **텍스처 추가 작업** — 제시된 2칸 모델은 1칸 아틀라스를 세로 2배로 늘려 쓴다. 채택하면 `gen_guardian_statue.py`에 32행 높이용 아틀라스 2장(`guardian_statue_upper`, `_lower`)을 추가로 그려야 한다. 미리보기 시트에 그려진 형태가 비율 기준.
7. 기존 세이브 — 이미 놓인 1칸 수호상은 LOWER로 로드되고 위 칸이 비어 있으므로 `updateShape`에서 사라진다. 배포 전 월드에만 해당하면 무시, 아니면 로드 시 위 칸을 채우는 한 번짜리 보정이 필요하다.
