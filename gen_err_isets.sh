#!/bin/bash


IC=10
RSEED=498579486
RANDOM=$RSEED

arr=(2400 14400 28800 57600)


for i in "${arr[@]}";
do
    ODIR=instances/set_err$(($i / 120))_lclzd    
    ./scr/ginstV2.sh $ODIR iginput/localized/M3_II50_IS100_HVRHSR.txt $IC $RANDOM $i
done



