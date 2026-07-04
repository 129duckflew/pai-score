#!/bin/bash
# 验证房间可见性问题：
# User 1 创建房间后，User 2 的 /api/users/{id}/history 看不到该房间
# 需要: curl, python3, node + npm (ws 包用于 WebSocket)
#
# 用法: ./scripts/test-room-visibility.sh

set -e

BASE="http://localhost:8082"
WS_BASE="ws://localhost:8082"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================"
echo "  房间可见性测试"
echo "========================================"
echo ""

# ── Check prerequisites ──
echo "=== 检查依赖 ==="
if ! command -v node &>/dev/null; then echo "❌ 需要 node"; exit 1; fi
echo "  ✔ node 已就绪"
echo ""

# ── Step 1: 注册 User 1 (Alice) ──
echo "=== 1. 注册 User 1 (Alice) ==="
RES1=$(curl -s -X POST "$BASE/api/users/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"Alice"}')
TOKEN1=$(echo "$RES1" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
UID1=$(echo "$RES1" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "  id=$UID1  token=$TOKEN1"
echo ""

# ── Step 2: 注册 User 2 (Bob) ──
echo "=== 2. 注册 User 2 (Bob) ==="
RES2=$(curl -s -X POST "$BASE/api/users/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"Bob"}')
TOKEN2=$(echo "$RES2" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
UID2=$(echo "$RES2" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "  id=$UID2  token=$TOKEN2"
echo ""

# ── Step 3: 验证初始历史都为空 ──
echo "=== 3. 验证初始历史都为空 ==="
curl -s "$BASE/api/users/$UID1/history" | python3 -c "import sys,json;d=json.load(sys.stdin);print(f'  Alice 历史: {d}')"
curl -s "$BASE/api/users/$UID2/history" | python3 -c "import sys,json;d=json.load(sys.stdin);print(f'  Bob   历史: {d}')"
echo ""

# ── Step 4: User 1 创建房间 (WebSocket) ──
echo "=== 4. User 1 (Alice) 创建房间 ==="
ROOM_CODE=$(node "$SCRIPT_DIR/create-room.js" "$TOKEN1" "$WS_BASE" 2>&1)
echo "  房间码: $ROOM_CODE"
echo ""

# ── Step 5: 再次查询历史 ──
echo "=== 5. 验证 Alice 能看到房间, Bob 看不到 ==="
HIST1=$(curl -s "$BASE/api/users/$UID1/history")
HIST2=$(curl -s "$BASE/api/users/$UID2/history")
echo "  Alice 历史: $HIST1"
echo "  Bob   历史: $HIST2"
ALICE_COUNT=$(echo "$HIST1" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))")
BOB_COUNT=$(echo "$HIST2" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))")
echo "  Alice 房间数: $ALICE_COUNT, Bob 房间数: $BOB_COUNT"
echo ""

# ── Step 6: 断言 ──
echo "=== 6. 结果判定 ==="
if [ "$ALICE_COUNT" -gt 0 ] && [ "$BOB_COUNT" -eq 0 ]; then
  echo "  ✅ 符合预期: User 2 看不到 User 1 的房间 (Bug 已复现)"
  echo ""
  echo "  解释: LobbyView 调用 /api/users/{userId}/history"
  echo "       只返回该用户 room_players 表中的房间."
  echo "       User 2 没有加入 User 1 的房间, 所以看不到."
  exit 0
else
  echo "  ❌ 不符合预期"
  echo "  Alice 房间数=$ALICE_COUNT, Bob 房间数=$BOB_COUNT"
  exit 1
fi
