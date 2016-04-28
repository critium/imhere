#!/bin/sh
export CURRWD=`pwd`

/usr/local/bin/ctags --tag-relative -Rf.git/tags.$$ --exclude=.git --exclude=*\\target --exclude=*\\deploy --languages=-javascript,+sql,+scala
mv .git/tags.$$ .git/tags
cd $CURRWD
