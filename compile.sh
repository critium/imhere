sbt -Dsbt.log.noformat=true ';compile;test:compile' | tee /tmp/compile.txt
printf '\a'
