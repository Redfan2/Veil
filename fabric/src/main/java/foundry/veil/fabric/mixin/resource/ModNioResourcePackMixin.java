package foundry.veil.fabric.mixin.resource;

import foundry.veil.Veil;
import foundry.veil.ext.PackResourcesExtension;
import net.fabricmc.fabric.api.resource.ModResourcePack;
import net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Mixin(ModNioResourcePack.class)
public abstract class ModNioResourcePackMixin implements ModResourcePack, PackResourcesExtension {

    @Shadow
    @Final
    private List<Path> basePaths;

    @Shadow
    @Final
    private ModMetadata modInfo;

    @Shadow
    @Final
    private Map<PackType, Set<String>> namespaces;

    @Override
    public void veil$listResources(PackResourceConsumer consumer) {
        for (Path basePath : this.basePaths) {
            String separator = basePath.getFileSystem().getSeparator();

            for (Map.Entry<PackType, Set<String>> entry : this.namespaces.entrySet()) {
                PackType type = entry.getKey();

                for (String namespace : entry.getValue()) {
                    Path nsPath = basePath.resolve(type.getDirectory()).resolve(namespace);
                    if (!Files.exists(nsPath)) {
                        continue;
                    }

                    try {
                        Files.walkFileTree(nsPath, new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                String filename = nsPath.relativize(file).toString().replace(separator, "/");
                                ResourceLocation name = ResourceLocation.tryBuild(namespace, filename);

                                if (name != null) {
                                    consumer.accept(type, name, nsPath, file, PackResourcesExtension.findDevPath(basePath, file));
                                }

                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (IOException e) {
                        Veil.LOGGER.warn("findResources in namespace {}, mod {} failed!", namespace, this.modInfo.getId(), e);
                    }
                }
            }
        }
    }

    @Override
    public @Nullable IoSupplier<InputStream> veil$getIcon() {
        ModContainer modContainer = FabricLoader.getInstance().getModContainer(this.modInfo.getId()).orElseThrow();
        return this.modInfo.getIconPath(20).flatMap(modContainer::findPath).<IoSupplier<InputStream>>map(path -> () -> Files.newInputStream(path)).orElse(null);
    }

    @Override
    public Stream<PackResources> veil$listPacks() {
        String id = this.modInfo.getId();
        if (!"fabric-api".equalsIgnoreCase(id) && id.startsWith("fabric") && this.modInfo.containsCustomValue("fabric-api:module-lifecycle")) {
            // Skip fabric apis
            return Stream.empty();
        }

        return Stream.of(this);
    }
}
