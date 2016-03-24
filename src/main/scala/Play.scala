//import java.io.ByteArrayInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.net.Socket;


//import javax.sound.sampled.AudioFormat;
//import javax.sound.sampled.AudioInputStream;
//import javax.sound.sampled.AudioSystem;
//import javax.sound.sampled.DataLine;
//import javax.sound.sampled.SourceDataLine;

//import org.apache.commons.io.IOUtils;

//class ClientStream{
  //val isl = new IncomingSoundListener();
  //val format:AudioFormat  = getAudioFormat();
  //var is:InputStream ;
  //var client:Socket ;
  ////val String serverName = "192.168.2.8";
  ////val int port=3000;
  ////val boolean inVoice = true;

  //isl.runListener();

  //def getAudioFormat():AudioFormat {
    //var sampleRate:Float = 16000.0F;
    //var sampleSizeBits:Int = 16;
    //var channels:Int = 1;
    //var signed = true;
    //var bigEndian = false;

    //return new AudioFormat(sampleRate, sampleSizeBits, channels, signed, bigEndian);
  //}

  //class IncomingSoundListener {
    //def runListener():Unit {
      //try{
        ////println("Connecting to server:"+serverName+" Port:"+port);
        ////client = new Socket(serverName,port);
        ////println("Connected to: "+client.getRemoteSocketAddress());
        ////println("Listening for incoming audio.");
        ////DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class,format);
        ////SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        ////speaker.open(format);
        ////speaker.start();

        ////while(inVoice){
          ////is = client.getInputStream();
          ////byte[] data = IOUtils.toByteArray(is);
          ////ByteArrayInputStream bais = new ByteArrayInputStream(data);
          ////AudioInputStream ais = new AudioInputStream(bais,format,data.length);
          ////int bytesRead = 0;
          ////if((bytesRead = ais.read(data)) != -1){
            ////println("Writing to audio output.");
            ////speaker.write(data,0,bytesRead);

            //////                 bais.reset();
          ////}
          ////ais.close();
          ////bais.close();

        ////}

        ////speaker.drain();
        ////speaker.close();
        ////println("Stopped listening to incoming audio.");
      //}catch(Exception e){
        //e.printStackTrace();
      //}
    //}
  //}

  ////public static void main(String [] args) throws IOException{
    ////new ClientStream();
  ////}
//}
