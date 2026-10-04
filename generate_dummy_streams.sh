#!/usr/bin/env bash

set -euo pipefail

# Default configuration values
DEFAULT_SERVER="rtsp://nginx-rev.lan.himpon.net:8554"
DEFAULT_COUNT=4
DEFAULT_PREFIX="stream"
DEFAULT_TYPE="testsrc"
DEFAULT_FPS=15
DEFAULT_RES="640x480"
DEFAULT_TRANSPORT="tcp"

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  -s, --server URI        Target MediaMTX RTSP server (default: ${DEFAULT_SERVER})
  -n, --count NUM         Number of streams to spawn (default: ${DEFAULT_COUNT})
  -p, --prefix NAME       Stream path prefix (default: "${DEFAULT_PREFIX}")
  -t, --type TYPE         Source pattern: testsrc | mandelbrot | smptebars (default: ${DEFAULT_TYPE})
  -r, --resolution RES    Video resolution WxH (default: ${DEFAULT_RES})
  -f, --fps NUM           Framerate (default: ${DEFAULT_FPS})
  --transport PROTO       RTSP transport: tcp | udp (default: ${DEFAULT_TRANSPORT})
  -h, --help              Show this help message
EOF
  exit 1
}

# Parse command-line flags
SERVER="$DEFAULT_SERVER"
COUNT="$DEFAULT_COUNT"
PREFIX="$DEFAULT_PREFIX"
TYPE="$DEFAULT_TYPE"
RES="$DEFAULT_RES"
FPS="$DEFAULT_FPS"
TRANSPORT="$DEFAULT_TRANSPORT"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--server) SERVER="$2"; shift 2 ;;
    -n|--count) COUNT="$2"; shift 2 ;;
    -p|--prefix) PREFIX="$2"; shift 2 ;;
    -t|--type) TYPE="$2"; shift 2 ;;
    -r|--resolution) RES="$2"; shift 2 ;;
    -f|--fps) FPS="$2"; shift 2 ;;
    --transport) TRANSPORT="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "Error: Unknown argument $1"; usage ;;
  esac
done

SERVER="${SERVER%/}"

case "$TYPE" in
  testsrc) LAVFI_INPUT="testsrc=size=${RES}:rate=${FPS}" ;;
  mandelbrot) LAVFI_INPUT="mandelbrot=size=${RES}:rate=${FPS}" ;;
  smptebars) LAVFI_INPUT="smptebars=size=${RES}:rate=${FPS}" ;;
  *) echo "Error: Unknown stream type"; exit 1 ;;
esac

PIDS=()

cleanup() {
  echo -e "\nStopping all dummy streams..."
  for pid in "${PIDS[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  wait 2>/dev/null || true
  echo "All streams stopped."
  exit 0
}

trap cleanup SIGINT SIGTERM

echo "=========================================="
echo " Starting $COUNT RTSP Dummy Stream(s)"
echo " Pattern:    $TYPE"
echo " Resolution: $RES @ ${FPS}fps"
echo " Target:     $SERVER"
echo " Transport:  $TRANSPORT"
echo "=========================================="

for i in $(seq 1 "$COUNT"); do
  STREAM_NAME="${PREFIX}_${i}"
  STREAM_URL="${SERVER}/${STREAM_NAME}"

  # -vf drawtext adds the stream name over the video. 
  # It draws white text on a semi-transparent black background.
  ffmpeg -re -f lavfi -i "$LAVFI_INPUT" \
    -vf "drawtext=text='${STREAM_NAME}':x=20:y=20:fontsize=48:fontcolor=white:box=1:boxcolor=black@0.7:boxborderw=10" \
    -c:v libx264 -preset ultrafast -tune zerolatency \
    -pix_fmt yuv420p -profile:v baseline -g "$FPS" \
    -rtsp_transport "$TRANSPORT" \
    -f rtsp "$STREAM_URL" \
    -loglevel error &

  PID=$!
  PIDS+=("$PID")
  echo " [+] Launched $STREAM_NAME (PID: $PID) -> $STREAM_URL"
done

echo ""
echo "Press Ctrl+C to terminate all streams."
wait
