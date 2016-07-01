import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.*

adrs = inputAS.get("ADR");

for (adr in adrs) {
	sentences = Utils.getOverlappingAnnotations(inputAS, adr, "Sentence");
	for (s in sentences) {
		String sentence = Utils.stringFor(doc, s);
		adr.getFeatures().put("sentence", sentence);
	}
}