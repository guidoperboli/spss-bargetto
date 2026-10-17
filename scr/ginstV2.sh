#!/bin/bash

DD=${1}
IF=$(readlink -e ${2})
IC=${3}
RSEED=${4}
SCS=${5}

SH=/home/roberto/Workspace/cpss/scr/geninst.sh
PY=/home/roberto/Workspace/cpss/py/ignr/ignrapp.py
RANDOM=$RSEED

mkdir -p $DD
cd $DD
cp -v $IF .
$SH $PY $IF $(($RANDOM * $RANDOM * $RANDOM)) $IC $SCS
cd ..
