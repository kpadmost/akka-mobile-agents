package com.kpadmost.boardactors;

import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.ShardingMessageExtractor;

public class MyMessageExtractor<M> extends ShardingMessageExtractor<ShardingEnvelope<M>, M> {
    @Override
    public String entityId(ShardingEnvelope<M> message) {
        return message.entityId();
    }

    @Override
    public String shardId(String entityId) {
        return "shard_" + entityId;
    }

    @Override
    public M unwrapMessage(ShardingEnvelope<M> message) {
        return message.message();
    }
}