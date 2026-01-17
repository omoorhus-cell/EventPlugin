package org.tekkabyte.eventPlugin.world;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public class VoidFlatGenerator extends ChunkGenerator {

    private static final int BASE_Y = 64;

    @Override
    public ChunkData generateChunkData(
            World world,
            Random random,
            int chunkX,
            int chunkZ,
            BiomeGrid biome
    ) {
        ChunkData data = createChunkData(world);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                data.setBlock(x, BASE_Y - 4, z, Material.BEDROCK);
                data.setBlock(x, BASE_Y - 3, z, Material.DIRT);
                data.setBlock(x, BASE_Y - 2, z, Material.DIRT);
                data.setBlock(x, BASE_Y - 1, z, Material.DIRT);
                data.setBlock(x, BASE_Y, z, Material.GRASS_BLOCK);
            }
        }

        return data;
    }
}