package topology;

import backtype.storm.Config;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.generated.StormTopology;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;

import java.io.FileNotFoundException;

import static topology.Constants.*;
import static topology.StormConfigManager.getInt;
import static topology.StormConfigManager.readConfig;

/**
 * Created by Intern04 on 4/8/2014.
 */
public class tomVLDTopology {



    public static void main(String args[]) throws InterruptedException, AlreadyAliveException, InvalidTopologyException, FileNotFoundException {
        if (args.length != 1) {
            System.out.println("Enter path to config file!");
            System.exit(0);
        }
        Config conf = readConfig(args[0]);

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("retriever", new tomFrameSpout(), getInt(conf, "TomFrameSpout.parallelism"))
                .setNumTasks(getInt(conf, "TomFrameSpout.tasks"));

        builder.setBolt("patchGen", new tomPatchGenerateBolt(), getInt(conf, "TomPatchGen.parallelism"))
                .allGrouping("retriever", RAW_FRAME_STREAM)
                .setNumTasks(getInt(conf, "TomPatchGen.tasks"));

        builder.setBolt("processor", new PatchProcessorBolt(), getInt(conf, "PatchProcessorBolt.parallelism"))
                .shuffleGrouping("patchGen", PATCH_STREAM)
                .allGrouping("processor", LOGO_TEMPLATE_UPDATE_STREAM)
                .allGrouping("intermediate", CACHE_CLEAR_STREAM)
                .directGrouping("patchGen", RAW_FRAME_STREAM)
                .setNumTasks(getInt(conf, "PatchProcessorBolt.tasks"));

        builder.setBolt("intermediate", new PatchAggregatorBolt(), getInt(conf, "PatchAggregatorBolt.parallelism"))
                .fieldsGrouping("processor", DETECTED_LOGO_STREAM, new Fields("frameId"))
                .setNumTasks(getInt(conf, "PatchAggregatorBolt.tasks"));

        builder.setBolt("aggregator2", new RedisFrameAggregatorBolt2(), getInt(conf, "FrameAggregatorBolt.parallelism"))
                .globalGrouping("intermediate", PROCESSED_FRAME_STREAM)
                .globalGrouping("retriever", RAW_FRAME_STREAM)
                .setNumTasks(getInt(conf, "FrameAggregatorBolt.tasks"));

        StormTopology topology = builder.createTopology();

        conf.setNumWorkers(getInt(conf, "numberOfWorkers"));
        StormSubmitter.submitTopology("tomVLDTop", conf, topology);

    }
}