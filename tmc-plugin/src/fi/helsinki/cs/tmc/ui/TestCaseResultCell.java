package fi.helsinki.cs.tmc.ui;

import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.langs.domain.TestResult;
import fi.helsinki.cs.tmc.model.SourceFileLookup;
import fi.helsinki.cs.tmc.utilities.ExceptionUtils;

import com.google.common.collect.ImmutableList;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.apache.commons.lang3.StringEscapeUtils;

import org.openide.cookies.EditorCookie;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.Line;

public final class TestCaseResultCell {

    private static final Logger log = Logger.getLogger(TestCaseResultCell.class.getName());

    private static final Color FAIL_COLOR = new Color(0xED0000);
    private static final Color PASS_COLOR = new Color(0x6FD06D);
    private static final Color VALGRIND_FAILED_COLOR = new Color(0xFFD000);
    private static final Color FAIL_TEXT_COLOR = FAIL_COLOR.darker();
    private static final Color PASS_TEXT_COLOR = PASS_COLOR.darker();

    private final Exercise exercise;
    private final TestResult result;
    private final SourceFileLookup sourceFileLookup;
    private JButton detailedMessageButton;
    private final GridBagConstraints gbc = new GridBagConstraints();
    private final JPanel detailView;
    private final ResultCell resultCell;

    private static final Pattern JAVA_STACKTRACE_ELEMENT_PATTERN = Pattern.compile("([\\w.]+)(\\.\\w+|\\$([\\w\\.]+))\\((\\w+.java):(\\d+)\\)");
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(".*(?:src|test|lib)[/\\\\]((?:[^/\\\\]\\S*[/\\\\]?)):(\\d+).*");// "((?:[^/\\\\]\\S*[/\\\\])\\S+[/\\\\]\\S+):(\\d+)");
    public TestCaseResultCell(final Exercise exercise, final TestResult result, final SourceFileLookup sourceFileLookup) {

        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        this.exercise = exercise;
        this.result = result;
        this.sourceFileLookup = sourceFileLookup;
        this.detailView = createDetailView();

        final String title = (result.isSuccessful() ? "PASS: " : "FAIL: ") + result.getName();

        this.resultCell = new ResultCell(getResultColor(),
                getResultTextColor(),
                title,
                result.getMessage(),
                detailView);
    }

    public ResultCell getCell() {

        return resultCell;
    }

    private JPanel createDetailView() {

        final JPanel view = new JPanel();

        view.setLayout(new GridBagLayout());
        view.setBackground(Color.WHITE);

        if (result.getException() != null || result.getMessage() != null) {
            view.add(Box.createVerticalStrut(16), gbc);
            this.detailedMessageButton = new JButton(detailedMessageAction);
            gbc.weighty = 1.0; // Leave it so for the detailed message
            view.add(detailedMessageButton, gbc);
        } else {
            this.detailedMessageButton = null;
            return null;
        }

        return view;
    }

    private static class ExceptionDisplay extends JEditorPane {

        private StringBuilder htmlBuilder;
        private HashMap<String, ActionListener> linkHandlers;
        private int nextLinkId;

        public ExceptionDisplay() {
            this.htmlBuilder = new StringBuilder().append("<html><body>");
            this.linkHandlers = new HashMap<String, ActionListener>();
            this.nextLinkId = 1;

            this.setEditable(false);
            this.setContentType("text/html");
            this.setBackground(Color.WHITE);
            this.setFont(new JLabel().getFont());

            this.addHyperlinkListener(new HyperlinkListener() {
                @Override
                public void hyperlinkUpdate(HyperlinkEvent e) {
                    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        ActionListener listener = linkHandlers.get(e.getDescription());
                        if (listener != null) {
                            ActionEvent ae = new ActionEvent(ExceptionDisplay.this, ActionEvent.ACTION_PERFORMED, "link clicked");
                            listener.actionPerformed(ae);
                        }
                    }
                }
            });
        }

        private String htmlText(String text) {
            return nlToBr(escapeHtml(text.trim()));
        }

        private static String escapeHtml(String s) {
            return StringEscapeUtils.escapeHtml3(s);
        }

        private static String nlToBr(String message) {
            StringBuilder sb = new StringBuilder();
            String[] lines = message.split("\n");
            for (int i = 0; i < lines.length - 1; ++i) {
                sb.append(lines[i]).append("<br/>");
            }
            sb.append(lines[lines.length - 1]);
            return sb.toString();
        }

        public void addTextLine(String text) {
            htmlBuilder.append(htmlText(text)).append("<br />");
        }

        public void addBoldTextLine(String text) {
            htmlBuilder.append("<b>").append(htmlText(text)).append("</b>").append("<br />");
        }

        public void addLink(String text, ActionListener listener) {
            htmlBuilder.append("<a href=\"#link").append(nextLinkId).append("\">").append(htmlText(text)).append("</a>").append("<br />");
            linkHandlers.put("#link" + nextLinkId, listener);
            nextLinkId += 1;
        }

        public void finish() {
            htmlBuilder.append("</body></html>");
            this.setText(htmlBuilder.toString());
            htmlBuilder = new StringBuilder();
        }
    }

    private static class DetailedMessageDisplay extends JEditorPane {

        private String content;

        public DetailedMessageDisplay() {
            this.content = "";
            this.setEditable(false);
            this.setContentType("text/html");
            this.setBackground(UIManager.getColor("Label.background"));
        }

        public void setContent(String content) {
            this.content = "<html>"
                    + StringEscapeUtils.escapeHtml3(content)
                    .replaceAll(" ", "&nbsp;")
                    .replaceAll("\n", "<br />")
                    .replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")
                    + "</html>";
        }

        public void finish() {
            this.setText(content);
        }
    }

//    private Action valgrindAction = new AbstractAction("Show valgrind trace") {
//
//        @Override
//        public void actionPerformed(final ActionEvent event) {
//
//            detailView.remove(detailedMessageButton);
//
//            final DetailedMessageDisplay display = new DetailedMessageDisplay();
//            display.setBackground(Color.WHITE);
//            display.setContent(result.getMessage());
//            display.finish();
//
//            detailView.add(display, gbc);
//
//            resultCell.revalidate();
//            resultCell.repaint();
//        }
//
//    };
    private Action detailedMessageAction = new AbstractAction("Show detailed message") {

        @Override
        public void actionPerformed(ActionEvent event) {

            detailView.remove(detailedMessageButton);

            ExceptionDisplay display = new ExceptionDisplay();
            ImmutableList<String> ex;

            if (result.getException() != null && result.getException().size() > 0) {
                ex = result.getException();
            } else {
                ex = result.getDetailedMessage();
            }

            addException(display, ex, false);
            display.finish();

            detailView.add(display, gbc);

            resultCell.revalidate();
            resultCell.repaint();
        }

        private void addException(ExceptionDisplay display, ImmutableList<String> ex, boolean isCause) {
            if (ex.size() > 0) {
                display.addBoldTextLine(ex.get(0));
                ex = ex.subList(1, ex.size()); // Remove first of ImmutableList
            }

            addStackTraceLines(display, ex);
        }

        private void addStackTraceLines(ExceptionDisplay display, ImmutableList<String> stackTrace) {
            for (final String ste : stackTrace) {
                
                Matcher matcher = JAVA_STACKTRACE_ELEMENT_PATTERN.matcher(ste);
                Matcher pathMatcher = FILE_PATH_PATTERN.matcher(ste);
                boolean added = false;
                if (matcher.matches()) {
                    String packageAndClass = matcher.group(1);
                    final int row = Integer.parseInt(matcher.group(5));
                    final FileObject sourceFile = sourceFileLookup.findSourceFileFor(exercise, packageAndClass);

                    if (sourceFile != null && row > 0) {
                        display.addLink(ste, new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                openAtLine(sourceFile, row);
                            }
                        });
                        added = true;

                    }
                } else if (pathMatcher.matches()) {
                    String path = pathMatcher.group(1);
                    final int row = Integer.parseInt(pathMatcher.group(2));
                    if (path.endsWith(".java")) {
                        path = path.substring(0, path.length() - 5);
                    }
                    final FileObject sourceFile = sourceFileLookup.findSourceFileFor(exercise, path);
                    System.out.println("Source: " + path + "File: " + sourceFile);
                    if (sourceFile != null && row > 0) {
                        display.addLink(ste, new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                openAtLine(sourceFile, row);
                            }
                        });
                        added = true;

                    }
                    
                }
                if (!added) {
                    display.addTextLine(ste);
                }
            }
        }

        private void openAtLine(FileObject sourceFile, final int lineNum) {
            try {
                if (sourceFile.isValid()) {
                    DataObject dataObject = DataObject.find(sourceFile);

                    EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);
                    if (editorCookie != null) {
                        editorCookie.open(); // Asynchronous

                        LineCookie lineCookie = dataObject.getCookie(LineCookie.class);
                        if (lineCookie != null) {
                            Line line = lineCookie.getLineSet().getCurrent(lineNum - 1);
                            line.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                        }
                    }
                }
            } catch (Exception ex) {
                ExceptionUtils.logException(ex, log, Level.WARNING);
            }
        }
    };

    private Color getResultColor() {
//        if (valgrindFailed) {
//            return VALGRIND_FAILED_COLOR;
//        } else
        if (result.isSuccessful()) {
            return PASS_COLOR;
        } else {
            return FAIL_COLOR;
        }
    }

    private Color getResultTextColor() {
        if (result.isSuccessful()) {
            return PASS_TEXT_COLOR;
        } else {
            return FAIL_TEXT_COLOR;
        }
    }
}
