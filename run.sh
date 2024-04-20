#!/bin/bash

if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]; then
    echo "usage ./run.sh <path-to-input.json> <path-to-loop.json> <path-to-looppip.json>"
    exit 0
fi

INPUT=$1
OUTPUT_LOOP=$2
OUTPUT_LOOPPIP=$3

# shellcheck disable=SC2164
cd src
java -ea -cp .:./gson-2.10.1.jar Main "../$INPUT" "../$OUTPUT_LOOP" "../$OUTPUT_LOOPPIP"