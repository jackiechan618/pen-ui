package org.six11.sf.rec;

import java.awt.geom.Area;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static java.lang.Math.toDegrees;

import org.six11.sf.CornerFinder;
import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SegmentDelegate;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.data.RankedList;
import org.six11.util.data.Statistics;
import org.six11.util.gui.shape.Areas;
import org.six11.util.pen.Antipodal;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;


// ---------------------------------------------------------------------------------------------------
//
//
//          N o t e
//
//                   This class isn't used any longer. See SketchBook#analyzeForErase().
//
//
// ---------------------------------------------------------------------------------------------------
public class EraseGestureRecognizer extends SketchRecognizer {

  public EraseGestureRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
  }

  public Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in)
      throws UnsupportedOperationException {
    throw new UnsupportedOperationException("sorry babe");
  }

  public RecognizedRawItem applyRaw(Ink ink) throws UnsupportedOperationException {
    RecognizedRawItem ret = RecognizedRawItem.noop();
    long elapsed = ink.getSequence().getLast().getTime() - ink.getSequence().getFirst().getTime();
    if (elapsed > 500) {
      double dx = ink.getSequence().getRoughDX();
      double dy = ink.getSequence().getRoughDY();
      if (Math.min(dx, dy) > 7.0) {
        double area = ink.getSequence().getRoughArea();
        double density = ink.getSequence().getRoughDensity();
        if (area > 100 && density > 2.0) {
          ConvexHull hull = ink.getHull();
          final Area hullArea = new Area(hull.getHullShape());
          final Collection<Segment> doomed = model.pickDoomedSegments(hullArea);
          final Collection<Ink> doomedInk = model.pickDoomedInk(hullArea, ink);
          ret = new RecognizedRawItem(true, RecognizedRawItem.SCRIBBLE_TO_ERASE) {
            public void activate(SketchBook model) {
              if (doomedInk.size() > 0) {
                for (Ink ink : doomedInk) {
                  model.removeInk(ink);
                }
              } else {
                for (Segment seg : doomed) {
                  model.removeGeometry(seg);
                }
              }
              model.getEditor().drawStuff();
            }
          };
        }
      } else {
        bug("Rejecting Mark's favorite bug.");
      }
    }
    return ret;
  }

  

}