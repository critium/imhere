import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.util.Vector;
import java.util.Enumeration;
import java.io.*;
import javax.sound.sampled.*;
import java.awt.font.*;
import java.text.*;

public class JCapture {
  public static AudioFormat getAudioFormat()  {
    AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
    float rate = 44000f;
    int sampleSize = 16;
    boolean bigEndian = true;
    int channels = 2;

    System.out.println("FORMAT: enc:" + encoding.toString() + " r:" + rate + " ss:" + sampleSize + " c:" + channels + " be:" + bigEndian);
    return new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize/8)*channels, rate, bigEndian);
  }

  public static void main(String args[]) {
    double duration = 0;
    AudioInputStream audioInputStream = null;
    TargetDataLine line;

    // define the required attributes for our line,
    // and make sure a compatible line is supported.

    AudioFormat format = getAudioFormat();
    DataLine.Info info = new DataLine.Info(TargetDataLine.class,
        format);

    if (!AudioSystem.isLineSupported(info)) {
      System.out.println("Line matching " + info + " not supported.");
    }

    // get and open the target data line for capture.

    try {
      System.out.println("INFO: " + info);
      line = (TargetDataLine) AudioSystem.getLine(info);
      line.open(format, line.getBufferSize());
    } catch (LineUnavailableException ex) {
      System.out.println("Unable to open the line: " + ex);
      return;
    } catch (SecurityException ex) {
      System.out.println(ex.toString());
      return;
    } catch (Exception ex) {
      System.out.println(ex.toString());
      return;
    }

    // play back the captured audio data
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int frameSizeInBytes = format.getFrameSize();
    int bufferLengthInFrames = line.getBufferSize() / 8;
    int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
    byte[] data = new byte[bufferLengthInBytes];
    int numBytesRead;

    line.start();

    while (true) {
      if((numBytesRead = line.read(data, 0, bufferLengthInBytes)) == -1) {
        break;
      }
      out.write(data, 0, numBytesRead);
    }

    // we reached the end of the stream.  stop and close the line.
    line.stop();
    line.close();
    line = null;

    // stop and close the output stream
    try {
      out.flush();
      out.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    }

    // load bytes into the audio input stream for playback

    byte audioBytes[] = out.toByteArray();
    ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
    audioInputStream = new AudioInputStream(bais, format, audioBytes.length / frameSizeInBytes);

    long milliseconds = (long)((audioInputStream.getFrameLength() * 1000) / format.getFrameRate());
    duration = milliseconds / 1000.0;

    try {
      audioInputStream.reset();
    } catch (Exception ex) {
      ex.printStackTrace();
      return;
    }
  }
}
