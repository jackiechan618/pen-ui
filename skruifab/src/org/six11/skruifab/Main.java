package org.six11.skruifab;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.six11.skruifab.analysis.Analyzer;
import org.six11.skruifab.analysis.MergeCF;
import org.six11.skruifab.analysis.Stroke;
import org.six11.skruifab.gui.GraphicMessage;
import org.six11.skruifab.gui.PenButton;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.Components;
import org.six11.util.io.Preferences;
import org.six11.util.lev.NamedAction;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.HoverEvent;
import org.six11.util.pen.HoverListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;
import static org.six11.util.Debug.num;

/**
 * 
 **/
public class Main {

  public static final String PEN_COLOR = "pen color";
  public static final String PEN_THICKNESS = "pen thickness";

  private static Set<Main> instances = new HashSet<Main>();

  private static Map<String, Integer> debuggingBufferKeyBinds = new HashMap<String, Integer>();
  static {
    debuggingBufferKeyBinds.put("all points", 1);
    debuggingBufferKeyBinds.put("corners", 2);
    // ... and others go here ...
  }

  private Stroke seq;
  private Color penColor;
  private double penThickness = 1;

  // The currentSeq and last index are for managing the currently-in-progress ink stroke
  private GeneralPath inProgSeqGP;
  private boolean gpVisible;
  private int lastCurrentSequenceIdx;

  // sequence listeners are interested in pen activity
  private Set<SequenceListener> sequenceListeners;

  // hover listeners are interested in pen hover (in/out/move) activity
  private Set<HoverListener> hoverListeners;

  private List<ChangeListener> changeListeners;

  private List<Stroke> uninterpreted;

  private DrawingSurface ds;
  private DrawnStuff drawnStuff;
  private ApplicationFrame af;
  private Preferences prefs;
  private Arguments args;
  private Map<String, Action> actions = new HashMap<String, Action>();
  private Set<Action> anonActions = new HashSet<Action>();
  private Analyzer analyzer;

  private SketchHUD hud;

  public static void main(String[] in) throws IOException {
    Debug.useColor = false;
    Arguments args = getArgumentSpec();
    args.parseArguments(in);
    args.validate();
    Debug.enabled = args.hasFlag("debug");
    Debug.useColor = args.hasFlag("debug-color");
    if (Debug.enabled) {
      bug("debugging enabled!");
    }
    makeInstance(args);
  }

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Skrui Fab");
    args.setDocumentationProgram("Runs the Skrui Fab app.");

    //    args.addFlag("load-sketch", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
    //        "Indicate a sketch file to load.");
    //    args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_OPTIONAL,
    //        "Requests extended help. Use --help=corner-finder (for example) to "
    //            + "get help on a particular command.");
    return args;
  }

  public static Main makeInstance() {
    return makeInstance(new Arguments());
  }

  public static Main makeInstance(Arguments args) {
    final Main inst = new Main(args);
    instances.add(inst);
    inst.af.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        instances.remove(inst);
        if (instances.size() == 0) {
          System.exit(0);
        }
      }
    });
    return inst;
  }

  private Main(Arguments args) {
    bug("Making Main instance");
    this.args = args;
    drawnStuff = new DrawnStuff(); // new ArrayList<DrawnThing>();
    uninterpreted = new ArrayList<Stroke>();
    sequenceListeners = new HashSet<SequenceListener>();
    hoverListeners = new HashSet<HoverListener>();
    analyzer = new Analyzer();
    af = new ApplicationFrame("Skrui Fab");
    af.setNoQuitOnClose();
    ds = new DrawingSurface(this);
    makeAnonActions();
    attachKeyboardAccelerators(af.getRootPane());
    af.setLayout(new BorderLayout());
    setPenColor(Color.BLACK);
    setPenThickness(2.4f);
    af.add(ds, BorderLayout.CENTER);
    if (args.hasFlag("big")) {
      af.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
    } else {
      af.setSize(500, 400);
    }
    hud = new SketchHUD();
    af.setGlassPane(hud);
    hud.setVisible(true);
    bug("Added SketchHUD to application frame");
    af.center();

    PenButton interpretButton = new PenButton("interpret");
    interpretButton.setSize(30, 30);
    interpretButton.addActionListner(new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        interpret();
      }
    });
    hud.addButton(interpretButton);
    af.setVisible(true);
    new SillySpudTest(this).tmpMakeConstrainedDrawing();
    hud.addMessage(GraphicMessage.makeStandard("Skrui Fab Alpha"));
    ds.repaint();
  }

  public DrawnStuff getDrawnStuff() {
    return drawnStuff;
  }

  /**
   * Fires a simple event indicating some (potentially) visual aspect of the data has changed.
   */
  public void fireChange() {
    if (changeListeners != null) {
      ChangeEvent ev = new ChangeEvent(this);
      for (ChangeListener cl : changeListeners) {
        cl.stateChanged(ev);
      }
    }
  }

  /**
   * Registers a change listener, which is whacked every time some (potentially) visual aspect of
   * the soup has changed and the GUI should be repainted.
   */
  public void addChangeListener(ChangeListener lis) {
    if (changeListeners == null) {
      changeListeners = new ArrayList<ChangeListener>();
    }
    if (!changeListeners.contains(lis)) {
      changeListeners.add(lis);
    }
  }

  public void setPenColor(Color pc) {
    penColor = pc;
  }

  public void setPenThickness(double thick) {
    penThickness = thick;
  }

  /**
   * Returns a reference to the currently in-progress scribble, suitable for efficient drawing.
   */
  public Shape getCurrentSequenceShape() {
    return inProgSeqGP;
  }

  public boolean isCurrentSequenceShapeVisible() {
    return gpVisible;
  }

  public void setCurrentSequenceShapeVisible(boolean vis) {
    gpVisible = vis;
  }

  public void addSequenceListener(SequenceListener lis) {
    sequenceListeners.add(lis);
  }

  public void removeSequenceListener(SequenceListener lis) {
    sequenceListeners.remove(lis);
  }

  public void updateFinishedSequence(Stroke s) {
    DrawingBuffer db = s.getDrawingBuffer();
    if (db != null) {
      db.setVisible(false);
    }
    removeFinishedSequence(s);
    s.redraw();
    if (s != null && s.size() > 1) {
      drawnStuff.add(s);
    }
  }

  public void removeFinishedSequence(Stroke s) {
    if (s != null) {
      drawnStuff.remove(s);
    }
  }

  public Color getPenColor() {
    return penColor;
  }

  public double getPenThickness() {
    return penThickness;
  }

  public void addRawInputBegin(int x, int y, long t) {
    seq = new Stroke();
    if (penColor != null) {
      seq.setAttribute("pen color", penColor);
    }
    seq.setAttribute("pen thickness", penThickness);

    Pt pt = new Pt(x, y, t);
    seq.add(pt);
    gpVisible = true;
    SequenceEvent sev = new SequenceEvent(this, seq, SequenceEvent.Type.BEGIN);
    fireSequenceEvent(sev);
  }

  public void addRawInputProgress(int x, int y, long t) {
    // Avoid adding duplicate points to the end of the sequence.
    Pt pt = new Pt(x, y, t);
    if (seq.size() == 0 || !seq.getLast().isSameLocation(pt)) {
      seq.add(pt);
      SequenceEvent sev = new SequenceEvent(this, seq, SequenceEvent.Type.PROGRESS);
      fireSequenceEvent(sev);
      drawSequence();
    }
  }

  public void addRawInputEnd() {
    if (seq == null) {
      bug("seq is null, bailing");
      return;
    } else {
      bug("adding finished sequence");
      addUninterpretedSequence(seq);
      SequenceEvent sev = new SequenceEvent(this, seq, SequenceEvent.Type.END);
      fireSequenceEvent(sev);
      seq = null;
      lastCurrentSequenceIdx = 0;
      inProgSeqGP = null;
      gpVisible = false;
    }
  }

  public void addUninterpretedSequence(Stroke s) {
    if (s != null && s.size() > 1 && gpVisible) {
      DrawingBuffer buf = DrawingBufferRoutines.makeSequenceBuffer(s);
      s.setDrawingBuffer(buf);
      analyzer.processFinishedSequence(s);
      drawDebuggingLayers();
      drawnStuff.add(s);
      uninterpreted.add(s);
      interpret();
      visual("Stroke: " + s.size() + " points, " + num(s.length()) + " arc length");
    }
  }

  private void interpret() {
    // interpret the uninterpreted strokes in context of the structured model.
    bug("Interpret.");
    DrawingBuffer cornerBuf = drawnStuff.getNamedBuffer(debuggingBufferKeyBinds.get("corners")
        .toString());
    for (Stroke s : uninterpreted) {
      if (!s.hasAttribute(MergeCF.CORNERS_FOUND)) {
        MergeCF.analyze(s); // annotates individual points with CORNER among other things
        int numCorners = 0;
        for (Pt pt : s) {
          if (pt.getBoolean(MergeCF.CORNER)) {
            numCorners++;
            DrawingBufferRoutines.dot(cornerBuf, pt, 3, 0.8, Color.BLACK, Color.RED);
          }
        }
        visual(numCorners + " corners: ");
      }
    }

  }

  public void addHover(int x, int y, long when, HoverEvent.Type type) {
    fireHoverEvent(new HoverEvent(this, new Pt(x, y, when), type));
  }

  public void addHoverListener(HoverListener lis) {
    hoverListeners.add(lis);
  }

  public void removeHoverListener(HoverListener lis) {
    hoverListeners.remove(lis);
  }

  private void fireHoverEvent(HoverEvent ev) {
    for (HoverListener lis : hoverListeners) {
      lis.handleHoverEvent(ev);
    }
  }

  /**
   * Draws the portion of the current sequence that has not yet been drawn.
   */
  protected void drawSequence() {
    if (seq != null) {
      for (int i = lastCurrentSequenceIdx; i < seq.size(); i++) {
        Pt pt = seq.get(i);
        if (i == 0) {
          inProgSeqGP = new GeneralPath();
          inProgSeqGP.moveTo((float) pt.x, (float) pt.y);
        } else {
          inProgSeqGP.lineTo((float) pt.x, (float) pt.y);
        }
        lastCurrentSequenceIdx = i;
      }
    }
    ds.repaint();
  }

  private void fireSequenceEvent(SequenceEvent ev) {
    for (SequenceListener lis : sequenceListeners) {
      lis.handleSequenceEvent(ev);
    }
  }

  /**
   * Add actions to the anonActions list, which are accessed only via key presses.
   */
  private void makeAnonActions() {
    // pressing 0..9 whacks a visible layer, which are all debugging things.
    for (int i = 0; i < 10; i++) {
      drawnStuff.addNamedBuffer("" + i, new DrawingBuffer(), false);
      drawnStuff.getNamedBuffer("" + i).setVisible(false);
      drawnStuff.getNamedBuffer("" + i).setHumanReadableName("" + i);
      drawnStuff.getNamedBuffer("" + i).setComplainWhenDrawingToInvisibleBuffer(false);
      final int which = i;
      anonActions.add(new NamedAction("Whack Layer " + i, KeyStroke.getKeyStroke("" + which)) {
        public void activate() {
          if (drawnStuff.getNamedBuffer("" + which) == null) {
            drawnStuff.addNamedBuffer("" + which, new DrawingBuffer(), false);
          }
          whackLayer(which);
        }
      });
    }
  }

  protected void whackLayer(int i) {
    DrawingBuffer db = drawnStuff.getNamedBuffer("" + i);
    if (db != null) {
      boolean currentValue = db.isVisible();
      String msg = (!currentValue ? "Show" : "Hide") + " layer " + i
          + (db.hasHumanReadableName() ? " (" + db.getHumanReadableName() + ")" : "");
      visual(msg);
      db.setVisible(!currentValue);
      getDrawingSurface().repaint();
    } else {
      visual("Can't find buffer for layer: " + i);
    }
  }

  private void drawDebuggingLayers() {
    DrawingBuffer buf;
    // corners?
    buf = drawnStuff.getNamedBuffer(debuggingBufferKeyBinds.get("corners") + "");
    if (buf.isVisible()) {
      // TODO: draw corners.
    }

    // all points?
    buf = drawnStuff.getNamedBuffer(debuggingBufferKeyBinds.get("all points") + "");
    if (buf.isVisible()) {
      Collection<Pt> all = analyzer.getAllPoints().getPoints();
      for (Pt pt : all) {
        DrawingBufferRoutines.dot(buf, pt, 3.0, 0.5, Color.BLACK, Color.LIGHT_GRAY);
      }
    }
  }

  public DrawingSurface getDrawingSurface() {
    return ds;
  }

  /**
   * Asks the given root pane to listen for keystroke actions associated with our actions (for those
   * that have keyboard accellerators).
   */
  public final void attachKeyboardAccelerators(JRootPane rp) {
    for (Action action : actions.values()) {
      KeyStroke s = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
      if (s != null) {
        rp.registerKeyboardAction(action, s, JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
    }
    for (Action action : anonActions) {
      KeyStroke s = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
      if (s != null) {
        rp.registerKeyboardAction(action, s, JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
    }
  }

  public List<Stroke> getSequences() {
    List<Stroke> ret = new ArrayList<Stroke>();
    for (DrawnThing dt : drawnStuff.getDrawnThings()) {
      if (dt instanceof Stroke) {
        ret.add((Stroke) dt);
      }
    }
    return ret;
  }

  public void setProperty(String key, String value) {
    prefs.setProperty(key, value);
    try {
      prefs.save();
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public String getProperty(String key) {
    return prefs.getProperty(key);
  }

  public BufferedImage getContentImage() {
    BufferedImage image = new BufferedImage(ds.getWidth(), ds.getHeight(),
        BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    Components.antialias(g);
    ds.paintContent(g, false);
    return image;
  }

  public BufferedImage getRawInkImage() {
    BufferedImage image = new BufferedImage(ds.getWidth(), ds.getHeight(),
        BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    Components.antialias(g);
    for (DrawingBuffer buf : drawnStuff.getDrawingBuffers()) {
      buf.paste(g);
    }
    return image;
  }

  public void clearDrawing() {
    seq = null;
    drawnStuff.clear();
  }

  public Arguments getArguments() {
    return args;
  }

  public static void bug(String what) {
    Debug.out("Main", what);
  }

  public static void warn(String what) {
    System.out.println("  **WARNING**  " + what);
  }

  public void visual(String what) {
    bug(what);
    hud.addMessage(GraphicMessage.makeStandard(what));
  }

}
