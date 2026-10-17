#!/bin/bash

DD=${1}
IF=$(readlink -e ${2})
IC=${3}
RSEED=${4}

SH=/home/roberto/Workspace/cpss/scr/geninst.sh
PY=/home/roberto/Workspace/cpss/py/ignr/ignrapp.py
RANDOM=$RSEED
arr=(20 120)

mkdir -p $DD
cd $DD
cp -v $IF .
for i in "${arr[@]}";
do
    $SH $PY $IF $(($RANDOM * $RANDOM * $RANDOM)) $IC $i
done

cd ..
