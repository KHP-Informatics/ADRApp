package gate.creole.measurements;

import gate.creole.PackagedController;
import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.AutoInstanceParam;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;

import java.net.URL;
import java.util.List;

@CreoleResource(name = "ANNIE+Measurements", icon = "measurements", autoinstances = @AutoInstance(parameters = {
	@AutoInstanceParam(name="pipelineURL", value="resources/annie-measurements.xgapp"),
	@AutoInstanceParam(name="menu", value="Measurements")}))
public class ANNIEMeasurements extends PackagedController {

}
