package io.github.am4er.worldreplica.test

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import io.github.am4er.worldreplica.api.WorldReplica
import io.github.am4er.worldreplica.fabric.module.selection.Wand
import io.github.am4er.worldreplica.platform.job.BoxWalker
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("DEPRECATION")
class WorldReplicaGameTest {
    private val worldReplica get() = WorldReplica.get()

    @GameTest
    fun wandClicksSetCorners(helper: GameTestHelper) {
        val level = helper.level
        val player = helper.makeMockServerPlayerInLevel()
        val a = helper.absolutePos(BlockPos(1, 1, 1))
        val b = helper.absolutePos(BlockPos(3, 2, 4))
        helper.setBlock(BlockPos(1, 1, 1), Blocks.STONE)

        helper.assertTrue(Wand.isWand(Wand.create()), "wand is recognised")
        helper.assertFalse(Wand.isWand(ItemStack(Items.GOLDEN_AXE)), "plain golden axe is not a wand")

        player.setItemInHand(InteractionHand.MAIN_HAND, Wand.create())
        val left = AttackBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, a, Direction.UP)
        val right = UseBlockCallback.EVENT.invoker()
            .interact(player, level, InteractionHand.MAIN_HAND, BlockHitResult(Vec3.atCenterOf(b), Direction.UP, b, false))
        helper.assertEq(left, InteractionResult.SUCCESS, "left-click result")
        helper.assertEq(right, InteractionResult.SUCCESS, "right-click result")

        val selection = worldReplica.selections[player.uuid]
        helper.assertEq(selection?.pos1, a, "pos1")
        helper.assertEq(selection?.pos2, b, "pos2")
        helper.assertEq(selection?.volume(), 3L * 2L * 4L, "volume")

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GOLDEN_AXE))
        val plain = AttackBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, b, Direction.UP)
        helper.assertEq(plain, InteractionResult.PASS, "plain axe result")
        helper.assertEq(worldReplica.selections[player.uuid]?.pos1, a, "pos1 unchanged by plain axe")

        helper.succeed()
    }

    @GameTest
    fun boxWalkerResumesAfterEveryRow(helper: GameTestHelper) {
        val from = BlockPos(-20, 3, 7)
        val to = BlockPos(17, 5, 40)
        val walker = BoxWalker(from, to)
        val seen = HashSet<Long>()
        var rows = 0
        var columns = 0
        var pauses = 0
        while (!walker.walk(0L, { y, z, x0, x1 ->
                rows++
                helper.assertTrue(x0 shr 4 == x1 shr 4, "row $x0..$x1 stays in one chunk")
                for (x in x0..x1) {
                    helper.assertTrue(x in from.x..to.x && y in from.y..to.y && z in from.z..to.z, "$x,$y,$z is inside the box")
                    helper.assertTrue(seen.add(BlockPos.asLong(x, y, z)), "$x,$y,$z visited once")
                }
            }, { _, _ -> columns++ })
        ) pauses++

        helper.assertEq(seen.size, 38 * 3 * 34, "blocks visited")
        helper.assertEq(columns, 4 * 3, "chunk columns")
        helper.assertEq(pauses + 1, rows, "one row per call")
        helper.succeed()
    }

    @GameTest(maxTicks = 200)
    fun saveCommandWritesSchematic(helper: GameTestHelper) {
        val level = helper.level
        val stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)
        helper.setBlock(BlockPos(1, 1, 1), Blocks.STONE)
        helper.setBlock(BlockPos(2, 1, 1), stairs)
        helper.setBlock(BlockPos(1, 2, 2), Blocks.CHEST)
        helper.getBlockEntity(BlockPos(1, 2, 2), ChestBlockEntity::class.java).setItem(0, ItemStack(Items.DIAMOND, 7))

        val min = helper.absolutePos(BlockPos(1, 1, 1))
        val max = helper.absolutePos(BlockPos(3, 3, 2))
        val player = helper.makeMockServerPlayerInLevel()
        worldReplica.selections.setCorner(player.uuid, 1, level.dimension(), max)
        worldReplica.selections.setCorner(player.uuid, 2, level.dimension(), min)

        val file = worldReplica.schematics.file("gametest_save")
        Files.deleteIfExists(file)
        runAs(player, "wr save gametest_save")

        helper.startSequence()
            .thenWaitUntil { helper.assertFalse(worldReplica.jobs.isRunning(player.uuid), "save finished") }
            .thenExecute {
                val blocks = helper.assertSchematicMatchesWorld(file, min, max, blockEntities = 1)
                val palette = blocks.getCompoundOrEmpty("Palette")
                helper.assertTrue(palette.keySet().any { it.startsWith("minecraft:oak_stairs[facing=east") }, "stairs keep their facing")
                val chest = blocks.getListOrEmpty("BlockEntities").getCompoundOrEmpty(0)
                helper.assertEq(chest.getIntArray("Pos").orElseThrow().toList(), listOf(0, 1, 1), "chest Pos")
                helper.assertEq(chest.getStringOr("Id", ""), "minecraft:chest", "chest Id")
                val item = chest.getCompoundOrEmpty("Data").getListOrEmpty("Items").getCompoundOrEmpty(0)
                helper.assertEq(item.getStringOr("id", ""), "minecraft:diamond", "chest item")
                helper.assertEq(item.getIntOr("count", 0), 7, "chest item count")
            }
            .thenSucceed()
    }

    @GameTest(maxTicks = 1200)
    fun saveAndLoadAcrossChunks(helper: GameTestHelper) {
        val level = helper.level
        val base = helper.absolutePos(BlockPos.ZERO).offset(0, 1, 600)
        val min = BlockPos((base.x and 15.inv()) + 7, base.y, (base.z and 15.inv()) + 7)
        val max = min.offset(36, 4, 36)
        forEachPos(min, max) { level.setBlock(it, pattern(it.x - min.x, it.y - min.y, it.z - min.z), FLAGS) }
        val chests = listOf(min.offset(8, 1, 8), min.offset(9, 1, 9), max)
        chests.forEachIndexed { i, pos -> placeChest(level, pos, ItemStack(Items.DIAMOND, i + 1)) }

        val player = helper.makeMockServerPlayerInLevel()
        worldReplica.selections.setCorner(player.uuid, 1, level.dimension(), min)
        worldReplica.selections.setCorner(player.uuid, 2, level.dimension(), max)
        val file = worldReplica.schematics.file("gametest_chunks")
        Files.deleteIfExists(file)
        val saveAnchor = min.offset(-3, 0, -3)
        standAt(player, saveAnchor)
        runAs(player, "wr save gametest_chunks")

        val shift = BlockPos(5, 2, 60)
        helper.startSequence()
            .thenWaitUntil { helper.assertFalse(worldReplica.jobs.isRunning(player.uuid), "save finished") }
            .thenExecute {
                helper.assertSchematicMatchesWorld(file, min, max, blockEntities = chests.size)
                standAt(player, saveAnchor.offset(shift))
                runAs(player, "wr load gametest_chunks")
            }
            .thenWaitUntil { helper.assertFalse(worldReplica.jobs.isRunning(player.uuid), "load finished") }
            .thenExecute {
                forEachPos(min, max) {
                    helper.assertEq(level.getBlockState(it.offset(shift)), level.getBlockState(it), "pasted block for ${it.toShortString()}")
                }
                chests.forEachIndexed { i, pos -> helper.assertChest(level, pos.offset(shift), Items.DIAMOND, i + 1) }
            }
            .thenSucceed()
    }

    @GameTest(maxTicks = 200)
    fun loadsWorldEditV2File(helper: GameTestHelper) {
        val level = helper.level
        val file = worldReplica.schematics.file("gametest_v2")
        Files.createDirectories(file.parent)
        NbtIo.writeCompressed(CompoundTag().apply {
            putInt("Version", 2)
            putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version())
            putShort("Width", 3)
            putShort("Height", 1)
            putShort("Length", 1)
            putIntArray("Offset", intArrayOf(1000, 64, 1000))
            put("Metadata", CompoundTag().apply {
                putInt("WEOffsetX", 1)
                putInt("WEOffsetY", 0)
                putInt("WEOffsetZ", 0)
            })
            putInt("PaletteMax", 3)
            put("Palette", CompoundTag().apply {
                putInt("minecraft:gold_block", 0)
                putInt("minecraft:chest[facing=west,type=single,waterlogged=false]", 1)
                putInt("minecraft:not_a_real_block", 2)
            })
            putByteArray("BlockData", byteArrayOf(0, 1, 2))
            put("BlockEntities", ListTag().apply {
                add(CompoundTag().apply {
                    putIntArray("Pos", intArrayOf(1, 0, 0))
                    putString("Id", "minecraft:chest")
                    put("Items", ListTag().apply {
                        add(CompoundTag().apply {
                            putByte("Slot", 0)
                            putString("id", "minecraft:emerald")
                            putInt("count", 5)
                        })
                    })
                })
            })
        }, file)

        val anchor = helper.absolutePos(BlockPos(2, 1, 2))
        helper.setBlock(BlockPos(5, 1, 2), Blocks.DIRT)
        val player = helper.makeMockServerPlayerInLevel()
        standAt(player, anchor)
        runAs(player, "wr load gametest_v2")

        helper.startSequence()
            .thenWaitUntil { helper.assertFalse(worldReplica.jobs.isRunning(player.uuid), "load finished") }
            .thenExecute {
                helper.assertEq(level.getBlockState(anchor.offset(1, 0, 0)).block, Blocks.GOLD_BLOCK, "gold block")
                val chest = level.getBlockState(anchor.offset(2, 0, 0))
                helper.assertEq(chest.block, Blocks.CHEST, "chest")
                helper.assertEq(chest.getValue(ChestBlock.FACING), Direction.WEST, "chest facing")
                helper.assertChest(level, anchor.offset(2, 0, 0), Items.EMERALD, 5)
                helper.assertTrue(level.getBlockState(anchor.offset(3, 0, 0)).isAir, "unknown block placed as air")
            }
            .thenSucceed()
    }

    @GameTest(maxTicks = 200)
    fun loadsFromWorldEditFolder(helper: GameTestHelper) {
        val level = helper.level
        val file = FabricLoader.getInstance().configDir.resolve("worldedit").resolve("schematics").resolve("gametest_worldedit.schem")
        Files.createDirectories(file.parent)
        NbtIo.writeCompressed(CompoundTag().apply {
            put("Schematic", CompoundTag().apply {
                putInt("Version", 3)
                putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version())
                putShort("Width", 1)
                putShort("Height", 1)
                putShort("Length", 1)
                putIntArray("Offset", intArrayOf(1, 0, 0))
                put("Blocks", CompoundTag().apply {
                    put("Palette", CompoundTag().apply { putInt("minecraft:diamond_block", 0) })
                    putByteArray("Data", byteArrayOf(0))
                })
            })
        }, file)

        val anchor = helper.absolutePos(BlockPos(2, 1, 2))
        val player = helper.makeMockServerPlayerInLevel()
        standAt(player, anchor)
        helper.assertTrue("gametest_worldedit" in worldReplica.schematics.names(), "listed from the WorldEdit folder")
        runAs(player, "wr load gametest_worldedit")

        helper.startSequence()
            .thenWaitUntil { helper.assertFalse(worldReplica.jobs.isRunning(player.uuid), "load finished") }
            .thenExecute {
                helper.assertEq(level.getBlockState(anchor.offset(1, 0, 0)).block, Blocks.DIAMOND_BLOCK, "diamond block")
                Files.deleteIfExists(file)
            }
            .thenSucceed()
    }

    @GameTest(maxTicks = 6000)
    fun savesAndLoadsMoreThanTenMillionBlocks(helper: GameTestHelper) {
        val level = helper.level
        val origin = helper.absolutePos(BlockPos.ZERO)
        val min = BlockPos(origin.x + 3000, origin.y - 40, origin.z)
        val max = min.offset(255, 159, 255)
        forEachPos(min, min.offset(255, 3, 255)) { level.setBlock(it, pattern(it.x, it.y, it.z), FLAGS) }
        for (i in 0..255) level.setBlock(min.offset(i, 4 + i * 155 / 255, 255 - i), Blocks.GOLD_BLOCK.defaultBlockState(), FLAGS)
        placeChest(level, min.offset(200, 100, 17), ItemStack(Items.EMERALD, 3))

        val player = helper.makeMockServerPlayerInLevel()
        worldReplica.selections.setCorner(player.uuid, 1, level.dimension(), min)
        worldReplica.selections.setCorner(player.uuid, 2, level.dimension(), max)
        val file = worldReplica.schematics.file("gametest_big")
        Files.deleteIfExists(file)
        val saveAnchor = min.offset(0, 50, -5)
        standAt(player, saveAnchor)
        runAs(player, "wr save gametest_big")

        val shift = BlockPos(400, 0, 0)
        helper.startSequence()
            .thenWaitUntil { helper.assertFalse(worldReplica.jobs.isRunning(player.uuid), "save finished") }
            .thenExecute {
                helper.assertSchematicMatchesWorld(file, min, max, blockEntities = 1)
                standAt(player, saveAnchor.offset(shift))
                runAs(player, "wr load gametest_big")
            }
            .thenWaitUntil { helper.assertFalse(worldReplica.jobs.isRunning(player.uuid), "load finished") }
            .thenExecute {
                forEachPos(min, max) {
                    helper.assertEq(level.getBlockState(it.offset(shift)), level.getBlockState(it), "pasted block for ${it.toShortString()}")
                }
                helper.assertChest(level, min.offset(200, 100, 17).offset(shift), Items.EMERALD, 3)
            }
            .thenSucceed()
    }

    @GameTest(maxTicks = 100)
    fun cancelStopsSave(helper: GameTestHelper) {
        val level = helper.level
        val player = helper.makeMockServerPlayerInLevel()
        worldReplica.selections.setCorner(player.uuid, 1, level.dimension(), helper.absolutePos(BlockPos(0, 0, 0)))
        worldReplica.selections.setCorner(player.uuid, 2, level.dimension(), helper.absolutePos(BlockPos(7, 7, 7)))
        val file = worldReplica.schematics.file("gametest_cancel")
        Files.deleteIfExists(file)

        runAs(player, "wr save gametest_cancel")
        helper.assertTrue(worldReplica.jobs.isRunning(player.uuid), "save started")
        runAs(player, "wr save gametest_cancel_again")
        helper.assertFalse(Files.exists(worldReplica.schematics.file("gametest_cancel_again")), "second save refused")
        runAs(player, "wr cancel")
        helper.assertFalse(worldReplica.jobs.isRunning(player.uuid), "save cancelled")

        helper.startSequence()
            .thenIdle(20)
            .thenExecute {
                helper.assertFalse(Files.exists(file), "cancelled save wrote no file")
                helper.assertFalse(Files.exists(worldReplica.schematics.file("gametest_cancel_again")), "refused save wrote no file")
            }
            .thenSucceed()
    }

    @GameTest(maxTicks = 400)
    fun postsToDiscordWhenSaved(helper: GameTestHelper) {
        val level = helper.level
        val bodies = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/webhook") { exchange ->
            bodies += exchange.requestBody.readAllBytes().decodeToString()
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()

        val config = FabricLoader.getInstance().configDir.resolve("worldreplica").resolve("discord.json")
        Files.createDirectories(config.parent)
        Files.writeString(config, """{"enabled": true, "webhookUrl": "http://127.0.0.1:${server.address.port}/webhook"}""")

        val player = helper.makeMockServerPlayerInLevel()
        worldReplica.selections.setCorner(player.uuid, 1, level.dimension(), helper.absolutePos(BlockPos(0, 0, 0)))
        worldReplica.selections.setCorner(player.uuid, 2, level.dimension(), helper.absolutePos(BlockPos(7, 7, 7)))
        Files.deleteIfExists(worldReplica.schematics.file("gametest_discord"))
        runAs(player, "wr reload")
        runAs(player, "wr save gametest_discord")

        helper.startSequence()
            .thenWaitUntil { helper.assertTrue(bodies.any { "gametest_discord.schem" in it }, "webhook called") }
            .thenExecute {
                Files.writeString(config, """{"enabled": false, "webhookUrl": ""}""")
                runAs(player, "wr reload")
                server.stop(0)

                val message = JsonParser.parseString(bodies.first { "gametest_discord.schem" in it }).asJsonObject
                val embed = message.getAsJsonArray("embeds")[0].asJsonObject
                helper.assertEq(embed.get("title").asString, "Schematic saved", "title")
                helper.assertEq(embed.get("description").asString, "**test-mock-player** saved `gametest_discord.schem`", "description")
                val blocks = embed.getAsJsonArray("fields").map { it.asJsonObject }.first { it.get("name").asString == "Blocks" }
                helper.assertEq(blocks.get("value").asString, "512", "blocks field")
                helper.assertEq(message.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size(), 0, "no mentions")
            }
            .thenSucceed()
    }

    @GameTest
    fun commandsNeedPermission(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        val root = helper.level.server.commands.dispatcher.root.getChild("wr")
        val regular = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL)
        val operator = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER)

        helper.assertFalse(root.canUse(regular), "non-op can see /wr")
        helper.assertTrue(root.canUse(operator), "op can see /wr")
        for (name in listOf("select", "save", "load", "cancel", "reload")) {
            helper.assertTrue(root.getChild(name).canUse(operator), "op can use /wr $name")
            helper.assertFalse(root.getChild(name).canUse(regular), "non-op can use /wr $name")
        }
        helper.succeed()
    }

    private fun runAs(player: ServerPlayer, command: String) {
        val source = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER)
        player.level().server.commands.performPrefixedCommand(source, command)
    }

    private fun standAt(player: ServerPlayer, pos: BlockPos) = player.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)

    private fun placeChest(level: ServerLevel, pos: BlockPos, item: ItemStack) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), FLAGS)
        (level.getBlockEntity(pos) as ChestBlockEntity).setItem(0, item)
    }

    private fun GameTestHelper.assertChest(level: ServerLevel, pos: BlockPos, item: net.minecraft.world.item.Item, count: Int) {
        val chest = level.getBlockEntity(pos) as? ChestBlockEntity
        assertTrue(chest != null, "chest at ${pos.toShortString()}")
        val stack = chest!!.getItem(0)
        assertEq(stack.item, item, "chest item at ${pos.toShortString()}")
        assertEq(stack.count, count, "chest item count at ${pos.toShortString()}")
    }

    private fun GameTestHelper.assertSchematicMatchesWorld(file: Path, min: BlockPos, max: BlockPos, blockEntities: Int): CompoundTag {
        assertTrue(Files.exists(file), "schematic file written")
        val schematic = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("Schematic")
        assertEq(schematic.getIntOr("Version", -1), 3, "Version")
        assertEq(schematic.getIntOr("DataVersion", -1), SharedConstants.getCurrentVersion().dataVersion().version(), "DataVersion")

        val width = schematic.getShortOr("Width", 0).toInt() and 0xFFFF
        val height = schematic.getShortOr("Height", 0).toInt() and 0xFFFF
        val length = schematic.getShortOr("Length", 0).toInt() and 0xFFFF
        assertEq(Triple(width, height, length), Triple(max.x - min.x + 1, max.y - min.y + 1, max.z - min.z + 1), "size")

        val offset = schematic.getIntArray("Offset").orElseThrow()
        val origin = schematic.getCompoundOrEmpty("Metadata").getCompoundOrEmpty("WorldEdit").getIntArray("Origin").orElseThrow()
        assertEq(BlockPos(offset[0] + origin[0], offset[1] + origin[1], offset[2] + origin[2]), min, "Offset + Origin")

        val blocks = schematic.getCompoundOrEmpty("Blocks")
        val paletteTag = blocks.getCompoundOrEmpty("Palette")
        val palette = arrayOfNulls<String>(paletteTag.size())
        for (key in paletteTag.keySet()) palette[paletteTag.getIntOr(key, -1)] = key
        val indices = decodeVarInts(blocks.getByteArray("Data").orElseThrow(), width.toLong() * height * length)

        val names = HashMap<BlockState, String>()
        for (y in 0 until height) for (z in 0 until length) for (x in 0 until width) {
            val state = level.getBlockState(min.offset(x, y, z))
            val expected = names.getOrPut(state) { BlockStateParser.serialize(state) }
            val actual = palette[indices[x + z * width + y * width * length]]
            if (actual != expected) fail("block at $x,$y,$z: expected $expected but was $actual")
        }
        assertEq(blocks.getListOrEmpty("BlockEntities").size, blockEntities, "block entity count")
        return blocks
    }

    private fun GameTestHelper.assertEq(actual: Any?, expected: Any?, what: String) =
        assertTrue(actual == expected, "$what: expected $expected but was $actual")

    private fun decodeVarInts(bytes: ByteArray, count: Long): IntArray {
        val out = IntArray(count.toInt())
        var n = 0
        var i = 0
        while (i < bytes.size) {
            var value = 0
            var shift = 0
            while (true) {
                val b = bytes[i++].toInt()
                value = value or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            check(n < out.size) { "more data than blocks" }
            out[n++] = value
        }
        check(n == out.size) { "$n values for ${out.size} blocks" }
        return out
    }

    private inline fun forEachPos(min: BlockPos, max: BlockPos, action: (BlockPos) -> Unit) {
        for (y in min.y..max.y) for (z in min.z..max.z) for (x in min.x..max.x) action(BlockPos(x, y, z))
    }

    private fun pattern(x: Int, y: Int, z: Int): BlockState = when (Math.floorMod(x * 7 + y * 13 + z * 3, 6)) {
        0 -> Blocks.STONE.defaultBlockState()
        1 -> Blocks.OAK_PLANKS.defaultBlockState()
        2 -> Blocks.GLASS.defaultBlockState()
        3 -> Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, HORIZONTAL[Math.floorMod(x + z, 4)])
        4 -> Blocks.GOLD_BLOCK.defaultBlockState()
        else -> Blocks.AIR.defaultBlockState()
    }

    private companion object {
        const val FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_SKIP_ALL_SIDEEFFECTS
        val HORIZONTAL = listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
    }
}
