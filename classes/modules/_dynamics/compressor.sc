MGU_compressor : MGU_AbstractModule {

	var <attack, <ratio, <release, <threshold;

	*new { |out, server, numInputs = 1, numOutputs = 1, name|
		^super.newCopyArgs(out, server, numInputs, numOutputs, name).init.initParameters
	}

	initParameters {

		attack = MGU_parameter(container, \attack, Float, [0.001, 0.1], 0.01, true);
		ratio = MGU_parameter(container, \ratio, Float, [0, 10], 2, true);
		release = MGU_parameter(container, \release, Float, [0.001, 0.1], 0.01, true);
		threshold = MGU_parameter(container, \threshold, Float, [-70, 0], -20, true);

		def = SynthDef(name, {
			var in, process;
			in = In.ar(inbus.kr, numInputs);
			process = FaustComp.ar(in, attack.kr, ratio.kr, release.kr, threshold.kr);
			Out.ar(out.kr, process)
		}).add;

	}

}

	