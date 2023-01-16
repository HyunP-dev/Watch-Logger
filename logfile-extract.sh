#!/bin/bash
echo "Watch Logger - File Extractor"
echo "================================================"
adb devices
read -p "Device: " device
echo "================================================"
echo "List of available heart rate log files"
adb -s "$device" exec-out run-as kr.ac.hallym.watchlogger ls files
echo ""
read -p "File: " filename
adb -s "$device" exec-out run-as kr.ac.hallym.watchlogger cat files/"$filename" > "$filename"
echo "================================================"
echo "Successfully extracted."
echo "================================================"