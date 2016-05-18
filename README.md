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
- [?] Convert audio client to channel
- [?] Convert audio server to channel
- [x] Add magic ring buffer to store byte arrays

# UI - https://github.com/critium/imhere-ui
- [ ] InProg. Add Electron UI
- [ ] InProg. Add Web Backend to work with UI
- [ ] InProg. Add PTT voice breakout
- [ ] InProg. Add PTT Room
- [x] InProg. Add Server REsponse on AudioLogin

# POLISH / Performance
- [ ] Add auto leveling on capture?
- [ ] Convert futures to threads?
- [ ] add variable quality, resample based on ui
- [ ] Add Encryption
- [ ] Add speex
- [ ] optimize speed
- [ ] optimize network

# TESTING
- [x] Add a way to send raw audio
- [x] Add a way to shortcut connections



#NOTES
Im going to have to rewrite to java.nio.channels.  to fix my room issue.

1. Each person should write to the byte channel.  This byte channel should automatically drain
if nobody is listening.

1. Need to decide if we use gathering or scattering byte channel.
  1. Gathering byte channel means we grab all the available people channels in the room and mix them.
  1. Scattering byte channel means that we scatter the audio bytes to the people we want to send to.

I think we should go wit the gathering approach.
1. On connection we register the byte channel and start writing to it.
1. On each audio view, they should be getting a refernce to that person's byte channel and mix it.


# OTHERS:
Use Disruptor ring buffer to get and write to channels

webconnect localhost:8080 1 test1 1 1
webconnect localhost:8080 2 test2 1 1
audioconnect
audioconnect blank
audioconnect ../samples/test1.raw
