package org.six11.skrui;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JPopupMenu;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.BoundingBox;
import org.six11.util.io.FileUtil;
import org.six11.util.lev.NamedAction;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.OliveDrawingSurface;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceIO;

/**
 * 
 **/
public class Main {

  private static OliveDrawingSurface ds;
  private static ApplicationFrame af;
  private static File currentFile;
  private static String batchModePdfFileName;

  private static final String ACTION_SAVE_AS = "save as";
  private static final String ACTION_SAVE = "save";
  private static final String ACTION_OPEN = "open";
  private static final String ACTION_SAVE_PDF = "save-pdf";

  public static void main(String[] in) {
    Debug.useColor = false;

    Arguments args = new Arguments();
    args.setProgramName("Skrui Hacks!");
    args.setDocumentationProgram("Runs various demos of the Skrui Hacks project.");

    args.addFlag("debug", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED,
        "If present, enable some debugging.");
    args.addFlag("load-sketch", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
        "Indicate a sketch file to load.");
    args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Requests extended help.");
    args.addFlag("pdf-output", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
        "Specify a PDF file to automatically output. Only useful with --load-sketch.");
    args.addFlag("no-ui", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED,
        "Supresses the user interface, which is useful in batch mode.");

    args.parseArguments(in);
    if (args.hasFlag("help")) {
      System.out.println(args.getDocumentation());
      System.exit(0);
    }
    args.validate();

    af = new ApplicationFrame("Olive Test GUI");
    ds = new OliveDrawingSurface();

    if (args.hasFlag("load-sketch")) {
      open(new File(args.getValue("load-sketch")));
    }

    if (args.hasFlag("pdf-output")) {
      batchModePdfFileName = args.getValue("pdf-output");
      savePdf();
    }

    if (args.hasFlag("no-ui") == false) {
      ds.setComponentPopupMenu(makePopup());
      af.setLayout(new BorderLayout());
      af.add(ds, BorderLayout.CENTER);
      af.setSize(500, 400);
      af.center();
      af.setVisible(true);
    }
  }

  private static JPopupMenu makePopup() {
    JPopupMenu pop = new JPopupMenu("Skrui Hacks");
    Map<String, Action> actions = new HashMap<String, Action>();

    // Save Action
    actions.put(ACTION_SAVE, new NamedAction("Save") {
      public void activate() {
        save(currentFile);
      }
    });
    pop.add(actions.get(ACTION_SAVE));

    // Save As Action
    actions.put(ACTION_SAVE_AS, new NamedAction("Save As..") {
      public void activate() {
        saveAs();
      }
    });
    pop.add(actions.get(ACTION_SAVE_AS));

    // Open Action
    actions.put(ACTION_OPEN, new NamedAction("Open...") {
      public void activate() {
        open();
      }
    });
    pop.add(actions.get(ACTION_OPEN));

    // Save PDF
    actions.put(ACTION_SAVE_PDF, new NamedAction("Save PDF...") {
      public void activate() {
        savePdf();
      }
    });
    pop.add(actions.get(ACTION_SAVE_PDF));

    return pop;
  }

  protected static void savePdf() {
    File outFile = null;
    if (batchModePdfFileName != null) {
      outFile = new File(batchModePdfFileName);
    } else {
      JFileChooser chooser = FileUtil.makeFileChooser(null, "sketch", "Raw Sketch Files");
      int result = chooser.showSaveDialog(ds);
      if (result == JFileChooser.APPROVE_OPTION) {
        outFile = chooser.getSelectedFile();
        if (!outFile.getName().endsWith(".pdf")) {
          outFile = new File(outFile.getParentFile(), outFile.getName() + ".pdf");
        }
      }
    }

    if (outFile != null) {
      FileOutputStream out;
      try {
        out = new FileOutputStream(outFile);
        List<DrawingBuffer> layers = ds.getSoup().getDrawingBuffers();
        BoundingBox bb = new BoundingBox();
        for (DrawingBuffer layer : layers) {
          layer.update();
          bb.add(layer.getBoundingBox());
        }
        int w = bb.getWidthInt();
        int h = bb.getHeightInt();
        Rectangle size = new Rectangle(w, h);
        Document document = new Document(size, 0, 0, 0, 0);

        try {
          PdfWriter writer = PdfWriter.getInstance(document, out);
          document.open();

          DefaultFontMapper mapper = new DefaultFontMapper();
          FontFactory.registerDirectories();

          PdfContentByte cb = writer.getDirectContent();
          PdfTemplate tp = cb.createTemplate(w, h);
          Graphics2D g2 = tp.createGraphics(w, h, mapper);
          tp.setWidth(w);
          tp.setHeight(h);
          bug("Painting content...");
          g2.translate(-bb.getX(), -bb.getY());
          ds.paintContent(g2, false);
          bug("Content painted.");
          g2.dispose();
          cb.addTemplate(tp, 0, 0);
        } catch (DocumentException ex) {
          bug(ex.getMessage());
        }
        document.close();
        bug("Wrote to " + outFile.getName());
      } catch (FileNotFoundException ex1) {
        ex1.printStackTrace();
      }
    }
  }

  private static void saveAs() {
    JFileChooser chooser = FileUtil.makeFileChooser(null, "sketch", "Raw Sketch Files");
    int result = chooser.showSaveDialog(ds);
    if (result == JFileChooser.APPROVE_OPTION) {
      File outFile = chooser.getSelectedFile();
      if (!outFile.getName().endsWith(".sketch")) {
        outFile = new File(outFile.getParentFile(), outFile.getName() + ".sketch");
      }
      setCurrentFile(currentFile);
      save(currentFile);
    }
  }

  private static void save(File outFile) {
    if (outFile == null) {
      saveAs();
    } else {
      try {
        FileUtil.createIfNecessary(outFile);
        FileUtil.complainIfNotWriteable(outFile);
        FileWriter fw = new FileWriter(outFile);
        List<Sequence> sequences = ds.getSoup().getSequences();
        SequenceIO.writeAll(sequences, fw);
        bug("Saved " + outFile.getName());
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  private static void open() {
    JFileChooser chooser = FileUtil.makeFileChooser(null, "sketch", "Raw Sketch Files");
    int result = chooser.showOpenDialog(ds);
    if (result == JFileChooser.APPROVE_OPTION) {
      File inFile = chooser.getSelectedFile();
      open(inFile);
    }
  }

  private static void open(File inFile) {
    try {
      FileUtil.complainIfNotReadable(inFile);
      BufferedReader in = new BufferedReader(new FileReader(inFile));
      List<Sequence> sequences = SequenceIO.readAll(in);
      bug("Read " + sequences.size() + " sequences from " + inFile.getAbsolutePath());
      ds.getSoup().clearDrawing();
      for (Sequence seq : sequences) {
        ds.getSoup().addFinishedSequence(seq);
      }
      setCurrentFile(inFile);
      bug("Reset drawing surface with new data.");
    } catch (IOException ex) {
      bug("Can't read file: " + inFile.getAbsolutePath());
      ex.printStackTrace();
    }
  }

  public static void setCurrentFile(File f) {
    if (f != null && f.exists()) {
      currentFile = f;
      af.setTitle(currentFile.getName());
    }
  }

  public static void bug(String what) {
    Debug.out("Main", what);
  }
}
