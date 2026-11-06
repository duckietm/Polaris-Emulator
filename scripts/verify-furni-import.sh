#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
module_root="$repo_root/Emulator"
items_source=""
furniture_data=""
nitro_root=""
report=""
items_json=false
require_swf=false
quiet=false

usage() {
    echo "Usage: $0 --items-source PATH --furniture-data PATH --nitro-root DIR --report PATH [--items-json] [--require-swf] [--quiet]"
}

while (($#)); do
    case "$1" in
        --items-source)
            items_source="${2:?Missing value for --items-source}"
            shift 2
            ;;
        --furniture-data)
            furniture_data="${2:?Missing value for --furniture-data}"
            shift 2
            ;;
        --nitro-root)
            nitro_root="${2:?Missing value for --nitro-root}"
            shift 2
            ;;
        --report)
            report="${2:?Missing value for --report}"
            shift 2
            ;;
        --items-json)
            items_json=true
            shift
            ;;
        --require-swf)
            require_swf=true
            shift
            ;;
        --quiet)
            quiet=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            exit 2
            ;;
    esac
done

for required in items_source furniture_data nitro_root report; do
    if [[ -z "${!required}" ]]; then
        usage >&2
        exit 2
    fi
done

jar=""
for candidate in "$module_root"/target/*-jar-with-dependencies.jar; do
    if [[ -f "$candidate" ]] && { [[ -z "$jar" ]] || [[ "$candidate" -nt "$jar" ]]; }; then
        jar="$candidate"
    fi
done
if [[ -z "$jar" ]] || find "$module_root/src" -type f -newer "$jar" -print -quit | grep -q .; then
    "$module_root/mvnw" -B -f "$module_root/pom.xml" -DskipTests package
    jar=""
    for candidate in "$module_root"/target/*-jar-with-dependencies.jar; do
        if [[ -f "$candidate" ]] && { [[ -z "$jar" ]] || [[ "$candidate" -nt "$jar" ]]; }; then
            jar="$candidate"
        fi
    done
fi

source_option="--items-sql-dump"
if [[ "$items_json" == true ]]; then
    source_option="--items"
fi

arguments=(
    -cp "$jar"
    polaris.tools.furni.FurniConsistencyCli
    "$source_option" "$(cd -- "$(dirname -- "$items_source")" && pwd)/$(basename -- "$items_source")"
    --furniture-data "$(cd -- "$(dirname -- "$furniture_data")" && pwd)/$(basename -- "$furniture_data")"
    --bundles "$nitro_root/nitro-assets/bundled/furniture"
    --icons "$nitro_root/swf/dcr/hof_furni/icons"
    --report "$report"
)
if [[ "$require_swf" == true ]]; then
    arguments+=(--swf "$nitro_root/swf/dcr/hof_furni")
fi
if [[ "$quiet" == true ]]; then
    arguments+=(--quiet)
fi

exec java "${arguments[@]}"
