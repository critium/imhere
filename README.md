# imhere


http://stackoverflow.com/questions/26550514/streaming-audio-from-microphone-with-java
http://tutorials.jenkov.com/java-networking/udp-datagram-sockets.html
http://jspeex.sourceforge.net/
TODO:

# Very basic POC
- [x] Split Objects, into playback and capture
- [x] Test Server-Client Streaming
- [ ] Convert to DataGram (UDP), move to much later on.  this is quite big
- [x] Create 3rd server, for relaying, and sending audio format
- [x] Collect n>2 mics
- [x] set configs
- [x] mix the sources, run on server
- [x] Add noise Filtering on capture

# Conver to channels and use disruptor - will need this because we want to mix on each individual channel.
- [x] Convert audio client to channel
- [x] Convert audio server to channel
- [x] Add magic ring buffer to store byte arrays

# UI - https://github.com/critium/imhere-ui
- [ ] InProg. Add Electron UI
- [x] InProg. Add Web Backend to work with UI
- [ ] InProg. Add PTT voice breakout
- [ ] InProg. Add PTT Room
- [x] InProg. Add Server Response on AudioLogin

# POLISH / Performance
- [x] Convert futures to threads?
- [ ] add variable quality, resample based on ui
- [ ] Add Encryption
- [ ] Add speex
- [ ] optimize speed
- [ ] optimize network
- [ ] full text search

# TESTING
- [x] Add a way to send raw audio
- [x] Add a way to shortcut connections

# CLIENT/SERVER
- [x] join command
- [x] list command
- [ ] !! talk command
- [ ] ! new room command
- [ ] ! close room command
- [ ] ! Add a 2nd mode that only sends/receives when talk is engaged vs auto-leveling
- [ ] Add auto leveling on capture (when on mode 1 which is promiscuous listen)?

# WIKI / SESSION
- [ ] ! create and collaborate documents in the room.
- [ ] ! build the chat.
- [ ] ! chats can get linked onto the document


# USEFUL COMMANDS:
Use Disruptor ring buffer to get and write to channels

webconnect localhost:8080 1 test1 1 1
webconnect localhost:8080 2 test2 1 1
audioconnect 1
audioconnect blank
audioconnect ../samples/test1.raw

1. Add 2nd mode on server and client
2. Get the talk button working
3. Get the UI Working (login, join, talk, logout)
