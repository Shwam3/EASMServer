package eastangliamapserver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import javax.swing.JTextArea;

public class CustomOutStream extends OutputStream
{
    private final JTextArea textArea;
    private final PrintStream defaultStream;

    public CustomOutStream(JTextArea textArea, PrintStream defaultStream)
    {
        this.textArea = textArea;
        this.defaultStream = defaultStream;
    }

    @Override
    public void write(int b) throws IOException
    {
        //synchronized (EastAngliaSignalMapServer.gui.logLock)
        //{
            try
            {
                boolean scrollToEnd = textArea.getCaretPosition() == textArea.getDocument().getLength();
                textArea.append(String.valueOf((char) b));

                if (scrollToEnd)
                    textArea.setCaretPosition(textArea.getDocument().getLength());

                defaultStream.write(b);
            }
            catch (Error er) {}
        //}
    }
}