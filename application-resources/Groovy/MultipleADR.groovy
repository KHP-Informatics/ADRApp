import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.*

sanns = inputAS.get("Sentence");

for (sann in sanns) {
	adrs = Utils.getOverlappingAnnotations(inputAS, sann, "ADR");
	if (adrs.size() >= 3) {
		for (adr in adrs) {
			adr.getFeatures().put("Negation", "Negated");
			adr.getFeatures().put("JAPERule", "MultipleADR");
		}
	}
}