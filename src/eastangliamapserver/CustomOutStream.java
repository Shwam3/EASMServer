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
                boolean scrollToEnd = textArea.getCaretPosition() >= textArea.getDocument().getLength();
                textArea.append(String.valueOf((char) b));

                if (scrollToEnd)
                    textArea.setCaretPosition(textArea.getDocument().getLength());

                defaultStream.write(b);

                /*if (textArea.getLineCount() > 500)
                {
                    int line = textArea.getLineOfOffset(textArea.getSelectionEnd());

                    textArea.setSelectionStart(0);
                    textArea.setSelectionEnd(0);

                    textArea.replaceRange("", 0, textArea.getLineEndOffset(textArea.getLineCount() - 500));

                    textArea.setSelectionStart(textArea.getLineEndOffset(line));
                    textArea.setSelectionEnd(textArea.getLineEndOffset(line));
                }*/
            }
            catch (/*BadLocationException | */Error e) {}
        //}
    }
}