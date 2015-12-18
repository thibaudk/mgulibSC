MGU_AbstractModule {

	classvar c_instanceCount;

	var <m_out, <>r_server, <m_numInputs, <m_numOutputs, <>m_name;
	var <m_container;
	var b_in, b_internal;
	var d_core, d_master
	var g_node, a_node, a_masterNode, a_sendNode;
	var a_send, a_defSend, a_levelSend;
	var <p_level, <p_mix;
	var <m_thisInstance;


	*new { |out = 0, server, numInputs = 1, numOutputs = 1, name|
		^this.newCopyArgs(m_out, r_server, m_numInputs, m_numOutputs, m_name)
	}

	init {

		// count
		c_instanceCount !? { c_instanceCount = c_instanceCount + 1 };
		c_instanceCount ?? { c_instanceCount = 1 };
		m_thisInstance = c_instanceCount;

		// defaults
		r_server ?? { r_server = Server.default };
		m_name ?? {m_name = this.class.asCompileString.split($_)[1] ++ "_" ++ m_thisInstance };

		// node arrays
		g_node = Group(1, 'addToTail');
		a_node = [];
		a_sendNode = [];
		a_sendNode = [];

		m_container = MGU_container(m_name, nil, g_node, 3127, this);
		p_level = MGU_parameter(m_container, \level, Float, [-96, 12], 0, true, \dB, \amp);
		p_out = MGU_parameter(m_container, \out, Integer, [0, inf], 0, false);

		if(numInputs > 0) {
			b_in = Bus.audio(r_server, m_numInputs);
			p_mix = MGU_parameter(m_container, \mix, Float, [0, 1], 0.5, true);
		};

	}

	registerToMinuit { |r_minuitInterface|
		r_minuitInterface.addContainer(m_container);
		m_container.parentContainer = r_minuitInterface;
	}

	initMasterOut {

		switch(type,
			\generator, {
				master_def = SynthDef(name ++ "_master", {
					var in = In.ar(master_internal, numChannels);
					var process = in * level.kr;
					Out.ar(out, process);
			}).add },
			\effect, {
				master_def = SynthDef(name ++ "_master", {
					var in_dry = In.ar(inbus, numChannels);
					var in_wet = In.ar(master_internal, numChannels);
					var process = FaustDrywet.ar(in_dry, in_wet, mix.kr) * level.kr;
					Out.ar(out, process);
		}).add });

	}

	sendSynth {

		switch(type,

			\effect, {
				nodeArray_master = nodeArray_master.add(
					Synth(name ++ "_master", [name ++ "_level", level.val, name ++ "_mix", mix.val],
						nodeGroup, 'addToTail'))},
			\generator, {
				nodeArray_master = nodeArray_master.add(
					Synth(name ++ "_master", [name ++ "_level", level.val],
						nodeGroup, 'addToTail'))});

		nodeArray = nodeArray.add(
			Synth(name, container.makeSynthArray.asOSCArgArray, nodeGroup, 'addToHead'));

		sendArray !? {
			sendArray.size.do({|i|
				nodeArray_send = nodeArray_send.add(
					Synth(name ++ "_send" ++ (i+1), [name ++ "_snd_" ++ sendArray[i].name,
						sendLevelArray[i].val], nodeGroup, 'addToTail'))
			})
		}

	}

	killSynth { |index|
		nodeArray_master[index].free;
		nodeArray_master.removeAt(index);
		nodeArray[index].free;
		nodeArray.removeAt(index);
	}

	killAllSynths {

		nodeArray.size.do({|i|
			nodeArray[0].free;
			nodeArray.removeAt(0);
		});

		nodeArray_master.size.do({|i|
			nodeArray_master[0].free;
			nodeArray_master.removeAt(0);
		});

		nodeArray_send.size.do({|i|
			nodeArray_send[0].free;
			nodeArray_send.removeAt(0);
		});

	}

	connectToModule { |module|
		out = module.inbus;
		this.initMasterOut();
	}

	addNewSend { |target|

		var outArray = [];

		target.inbus.numChannels.do({|i|
			outArray = outArray.add(target.inbus.index + i)
		});

		sendArray ?? { sendArray = [] };
		sendLevelArray ?? { sendLevelArray = [] };
		sendDefArray ?? { sendDefArray = [] };

		sendArray = sendArray.add(target);

		sendLevelArray = sendLevelArray.add(
			MGU_parameter(container, "snd_" ++ target.name,
				Float, [-96, 12], 0, true, \dB, \amp));

		sendDefArray = sendDefArray.add(
			SynthDef(name ++ "_send" ++ sendArray.size, {
				var in = In.ar(master_internal, numChannels);
				var process = in * sendLevelArray[sendLevelArray.size -1].kr;
				Out.ar(outArray, process);
			}).add;
		);
	}

	numChannels_ { |value|
		inbus !? { inbus.free };
		inbus = Bus.audio(server, numChannels);
	}

	generateUI { // shortcut
		container.generateUI;
	}

	// PRESET SUPPORT

	getPresetFolderPath {
		var folder_path;
		folder_path = Platform.userExtensionDir +/+ "mgulibSC/classes/modules/presets" +/+
		this.class.asCompileString;
		folder_path = folder_path.standardizePath;
		^folder_path
	}

	getPresetFilePath { |fileName|
		var file_path;
		file_path = this.getPresetFolderPath +/+ fileName ++ ".txt";
		file_path = file_path.standardizePath;
		^file_path
	}

	saveState { |fileName|
		var folder_path = this.getPresetFolderPath;
		var file_path = this.getPresetFilePath(fileName);
		var stateArray = [];
		var stateFile;
		this.instVarSize.do({|i|
			if(this.instVarAt(i).class == MGU_parameter, {
				this.instVarAt(i).val.postln;
				stateArray = stateArray.add(this.instVarAt(i).val);
			});
		});

		// if path doesn't exist, create path
		if(PathName(folder_path).isFolder == false, {
			folder_path.mkdir;
			("module: preset folder created at" + folder_path).postln;
		});

		// create or overwrite text file
		stateFile = File((file_path).standardizePath, "w+");
		stateFile.write(stateArray.asCompileString);
		stateFile.close;
		("module: preset file created at" + file_path).postln;
	}

	recallState { |fileName, interp = false, length = 2000, curve = \lin|

		// + preset interpolation to implement
		var folder_path = this.getPresetFolderPath();
		var file_path = this.getPresetFilePath(fileName);
		var stateFile;
		var stateArray = [];
		var j = 0;

		stateFile = File(file_path, "r");
		stateArray = stateFile.readAllString.interpret;

		this.instVarSize.do({|i|
			if(this.instVarAt(i).class == MGU_parameter, {
				this.instVarAt(i).val = stateArray[j];
				j = j + 1;
			})
		});
	}

	availablePresets { // returns preset list for this module -- TBI

	}

}


	