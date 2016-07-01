/*
 * Copyright (c) 2009-2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */

/**
 * This package contains a GATE PR which attempt to annotate and normalize
 * measurements appearing within documents. Each measurement is also normalized
 * back to SI units which allows measurements specified using different units to
 * be compared. For example 30cm and 300mm are both normalized to 0.3m. The
 * parsing of measurements is based upon a modified version of the <a
 * href="http://units-in-java.sourceforge.net/">Java port</a> of the <a
 * href="http://www.gnu.org/software/units/">GNU Units</a> package.
 * 
 * @see <a href="http://gate.ac.uk/userguide/sec:misc-creole:measurements">The
 *      GATE User Guide</a>
 * @author Mark A. Greenwood
 */
package gate.creole.measurements;

