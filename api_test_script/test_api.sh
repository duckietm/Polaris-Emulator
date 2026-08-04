#!/usr/bin/env bash
# Exercise every CMS API command against a target user. Requires bash, curl, openssl.
#
# Usage:
#   HOST=127.0.0.1 PORT=2096 KEY=cms-main SECRET=thisisatest USERID=9 ./cms-api-all-commands.sh
#
# Step mode (default): pauses after each command. Enter=next, a=run all, q=quit.
#   Disable with STEP=0 to fire everything at once.
#
# Destructive commands (disconnect / setrank / changeroomowner) are skipped unless
# you pass RUN_DESTRUCTIVE=1. Some commands need the user ONLINE or a valid
# external id (item/page/room/achievement/clothing); those may return an error
# envelope, which still confirms the call path works.
set -u

HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-2096}"
KEY="${KEY:-cms-main}"
SECRET="${SECRET:-thisisatest}"
BASE="http://${HOST}:${PORT}/api/cms"

USERID="${USERID:-1}"                    # primary target user id
TARGET="${TARGET:-1}"                    # second user (friend/ignore/stalk)
ROOM_ID="${ROOM_ID:-1}"
ITEM_ID="${ITEM_ID:-1}"                  # catalog item id for sendgift
CATALOG_PAGE="${CATALOG_PAGE:-1}"
ACHIEVEMENT_ID="${ACHIEVEMENT_ID:-1}"
CLOTHING_ID="${CLOTHING_ID:-1}"
RANK="${RANK:-1}"
BADGE="${BADGE:-ADM}"
RUN_DESTRUCTIVE="${RUN_DESTRUCTIVE:-0}"
STEP="${STEP:-1}"                        # 1 = pause after each command

pass=0; fail=0

pause() {
  [ "$STEP" = "1" ] || return 0
  printf '   [Enter = next | a = run all | q = quit] '
  local ans
  IFS= read -r ans < /dev/tty || { echo; return 0; }
  case "$ans" in
    q|Q) echo "aborted."; exit 0 ;;
    a|A) STEP=0 ;;
  esac
}

cmd() {  # $1 = command key, $2 = json data object
  local key="$1" data="$2"
  local body ts nonce sig code out body_out
  body="{\"key\":\"${key}\",\"data\":${data}}"
  ts="$(date +%s)"
  nonce="$(openssl rand -hex 12)"
  sig="$(printf '%s\n%s\n%s\n%s' "$KEY" "$ts" "$nonce" "$body" \
        | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $NF}')"
  out="$(curl -sS -m 10 -w $'\n%{http_code}' -X POST "${BASE}/command" \
    -H 'Content-Type: application/json' \
    -H "X-Cms-Key: ${KEY}" -H "X-Cms-Timestamp: ${ts}" \
    -H "X-Cms-Nonce: ${nonce}" -H "X-Cms-Signature: ${sig}" \
    --data-binary "$body")"
  code="${out##*$'\n'}"; body_out="${out%$'\n'*}"
  printf '%-22s HTTP %-3s  %s\n' "$key" "$code" "$body_out"
  if [ "$code" = "200" ]; then pass=$((pass+1)); else fail=$((fail+1)); fi
  pause
}

echo "Target user=$USERID  target2=$TARGET  (STEP=$STEP  RUN_DESTRUCTIVE=$RUN_DESTRUCTIVE)"
echo "--- alerts / messaging ---"
cmd alertuser        "{\"user_id\":${USERID},\"message\":\"CMS API test alert\"}"
cmd hotelalert       "{\"message\":\"CMS API hotel alert\"}"
cmd staffalert       "{\"message\":\"CMS API staff alert\"}"
cmd imagealertuser   "{\"user_id\":${USERID},\"bubble_key\":\"generic\",\"message\":\"hi\"}"
cmd imagehotelalert  "{\"bubble_key\":\"generic\",\"message\":\"hi all\"}"
cmd talkuser         "{\"type\":\"talk\",\"user_id\":${USERID},\"message\":\"hello\"}"

echo "--- economy ---"
cmd givecredits      "{\"user_id\":${USERID},\"credits\":100}"
cmd givepixels       "{\"user_id\":${USERID},\"pixels\":100}"
cmd givepoints       "{\"user_id\":${USERID},\"points\":100,\"type\":0}"
cmd sendgift         "{\"user_id\":${USERID},\"itemid\":${ITEM_ID},\"message\":\"gift\"}"
cmd sendroombundle   "{\"user_id\":${USERID},\"catalog_page\":${CATALOG_PAGE}}"
cmd giveuserclothing "{\"user_id\":${USERID},\"clothing_id\":${CLOTHING_ID}}"
cmd modifysubscription "{\"user_id\":${USERID},\"type\":\"HABBO_CLUB\",\"action\":\"add\",\"duration\":60}"

echo "--- users / profile ---"
cmd setmotto         "{\"user_id\":${USERID},\"motto\":\"set via CMS API\"}"
cmd givebadge        "{\"user_id\":${USERID},\"badge\":\"${BADGE}\"}"
cmd giverespect      "{\"user_id\":${USERID},\"respect_given\":1,\"respect_received\":1,\"daily_respects\":1}"
cmd progressachievement "{\"user_id\":${USERID},\"achievement_id\":${ACHIEVEMENT_ID},\"progress\":1}"
cmd updateuser       "{\"user_id\":${USERID},\"block_following\":-1}"
cmd changeusername   "{\"user_id\":${USERID},\"canChange\":true}"

echo "--- social / rooms ---"
cmd friendrequest    "{\"user_id\":${USERID},\"target_id\":${TARGET}}"
cmd ignoreuser       "{\"user_id\":${USERID},\"target_id\":${TARGET}}"
cmd stalkuser        "{\"user_id\":${USERID},\"follow_id\":${TARGET}}"
cmd forwarduser      "{\"user_id\":${USERID},\"room_id\":${ROOM_ID}}"

echo "--- moderation ---"
cmd muteuser         "{\"user_id\":${USERID},\"duration\":1}"
cmd modticket        "{\"sender_id\":${USERID},\"sender_username\":\"tester\",\"reported_id\":${TARGET},\"reported_username\":\"target\",\"reported_room_id\":0,\"message\":\"api ticket\"}"
cmd executecommand   "{\"user_id\":${USERID},\"command\":\":about\"}"

echo "--- cache reloads (empty data) ---"
cmd updatecatalog    "{}"
cmd updateitems      "{}"
cmd updatewordfilter "{}"
cmd updatewheel      "{}"
cmd updatesoundboard "{}"

if [ "$RUN_DESTRUCTIVE" = "1" ]; then
  echo "--- DESTRUCTIVE (RUN_DESTRUCTIVE=1) ---"
  cmd setrank        "{\"user_id\":${USERID},\"rank\":${RANK}}"
  cmd changeroomowner "{\"room_id\":${ROOM_ID},\"user_id\":${USERID},\"username\":\"\"}"
  cmd disconnect     "{\"user_id\":${USERID}}"
else
  echo "--- skipped (set RUN_DESTRUCTIVE=1): setrank, changeroomowner, disconnect ---"
fi

echo
echo "DONE. 200-OK: $pass   non-200: $fail"
echo "(non-200 is expected for commands needing an online user or a valid external id.)"
