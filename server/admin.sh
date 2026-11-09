#!/data/data/com.termux/files/usr/bin/bash
# Amministrazione dediche. La chiave NON va nel repo: esportala nella sessione.
#   export ADMIN_KEY='la-tua-chiave'
# Uso:
#   bash admin.sh list [N]            elenca le ultime dediche (id, utente, ip, testo)
#   bash admin.sh purge-ip  <ip>      cancella tutte le dediche di quell'impronta IP (prefisso ok) e BANNA
#   bash admin.sh purge-user <id>     cancella le dediche di un utente e BANNA
#   bash admin.sh purge-ded <id>      cancella una dedica (senza ban)
#   bash admin.sh upcoming "Artista" "Album" 2026-12-25 [url-copertina]   aggiunge un album in attesa
#   bash admin.sh upcoming-rm <id>    toglie un album in attesa
#   bash admin.sh unban-ip <ip> | unban-user <id>
URL="${SONORA_URL:-https://sonora-wrapped.sonorawrapped.workers.dev}"
[ -z "$ADMIN_KEY" ] && { echo "Imposta prima: export ADMIN_KEY='...'"; exit 1; }
H=(-H "x-admin-key: $ADMIN_KEY" -H "content-type: application/json")
post() { curl -s "${H[@]}" -X POST "$URL/v1/admin/$1" -d "$2"; echo; }
case "$1" in
  list) curl -s "${H[@]}" "$URL/v1/admin/ded?limit=${2:-50}" | python3 -c '
import sys,json,datetime
for i in json.load(sys.stdin).get("items",[]):
    t=datetime.datetime.fromtimestamp(i["ts"]/1000).strftime("%d/%m %H:%M")
    print(i["id"],t,"user="+str(i["user"])[:8],"ip="+i["ip"],"|",i["from"],"->",i["to"],":",i["msg"][:60])' ;;
  purge-ip)   post purge "{\"ip\":\"$2\",\"ban\":true}" ;;
  purge-user) post purge "{\"user\":\"$2\",\"ban\":true}" ;;
  purge-ded)  post purge "{\"ded\":\"$2\"}" ;;
  upcoming)   post upcoming "{\"artist\":\"$2\",\"album\":\"$3\",\"release\":$(date -d "$4" +%s)000,\"cover\":\"$5\"}" ;;
  upcoming-rm) post upcoming "{\"remove\":\"$2\"}" ;;
  unban-ip)   post unban "{\"ip\":\"$2\"}" ;;
  unban-user) post unban "{\"user\":\"$2\"}" ;;
  *) sed -n 2,14p "$0" ;;
esac
