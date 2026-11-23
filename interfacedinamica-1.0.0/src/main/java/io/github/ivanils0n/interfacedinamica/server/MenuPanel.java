package io.github.ivanils0n.interfacedinamica.server;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A tela de quem abriu, vista pelos outros jogadores: um painel flutuando ao lado dele, sempre virado para quem olha
 * (billboard), montado com entidades virtuais do próprio jogo (blocos e itens de exibição). Usa as posições reais
 * dos espaços da tela (Slot.x/y), então funciona com baú, fornalha, mesa e telas de outros mods.
 * Os blocos de exibição recebem a luz do lugar: o painel escurece à noite e em cavernas.
 */
public final class MenuPanel extends ElementHolder {
	/** Largura do painel em blocos para uma tela padrão (176 px). */
	private static final float WIDTH_BLOCKS = 1.1f;
	/** Blocos por pixel da tela. */
	private static final float U = WIDTH_BLOCKS / 176f;
	private static final float VIEW_RANGE = 0.5f;

	final Entity anchor;
	final ServerPlayer owner;
	final AbstractContainerMenu menu;
	private final int w, h;
	private final List<SlotView> slots = new ArrayList<>();
	private final List<DisplayElement> all = new ArrayList<>();
	private float lastYaw = Float.NaN;

	private record SlotView(Slot slot, ItemDisplayElement item, TextDisplayElement count, ItemStack[] last) {}

	/**
	 * @param anchor quem está com a tela aberta (o painel acompanha)
	 * @param owner  jogador que não deve ver o painel (ele já vê a própria tela); pode ser null
	 */
	public static MenuPanel create(Entity anchor, ServerPlayer owner, AbstractContainerMenu menu, Component title) {
		MenuPanel panel = new MenuPanel(anchor, owner, menu, title);
		EntityAttachment.ofTicking(panel, anchor);
		return panel;
	}

	private MenuPanel(Entity anchor, ServerPlayer owner, AbstractContainerMenu menu, Component title) {
		this.anchor = anchor;
		this.owner = owner;
		this.menu = menu;
		int maxX = 0, maxY = 0;
		for (Slot s : menu.slots) {
			if (!s.isActive()) continue;
			maxX = Math.max(maxX, s.x);
			maxY = Math.max(maxY, s.y);
		}
		this.w = Math.max(176, maxX + 16 + 8);
		this.h = Math.max(60, maxY + 16 + 8);

		// Moldura escura, fundo cinza claro e um quadradinho mais escuro por espaço (como a tela do jogo).
		rect(block("black_concrete"), -2, -2, w + 4, h + 4, 0f);
		rect(block("white_concrete"), 0, 0, w, h, 0.002f);
		for (Slot s : menu.slots) {
			if (!s.isActive()) continue;
			rect(block("gray_concrete"), s.x - 1, s.y - 1, 18, 18, 0.004f);
			rect(block("light_gray_concrete"), s.x, s.y, 16, 16, 0.005f);
			ItemDisplayElement item = new ItemDisplayElement();
			item.setItemDisplayContext(ItemDisplayContext.GUI);
			setup(item, new Vector3f(x(s.x + 8), y(s.y + 8), 0.012f), new Vector3f(16 * U, 16 * U, 0.001f));
			slots.add(new SlotView(s, item, null, new ItemStack[]{ItemStack.EMPTY}));
		}
		// O número de cada pilha (texto pequeno no canto de baixo do espaço).
		List<SlotView> withCount = new ArrayList<>();
		for (SlotView v : slots) {
			TextDisplayElement t = new TextDisplayElement(Component.empty());
			t.setBackground(0);
			t.setShadow(true);
			float sc = U * 40f;
			setup(t, new Vector3f(x(v.slot().x + 13), y(v.slot().y + 17), 0.02f), new Vector3f(sc, sc, sc));
			withCount.add(new SlotView(v.slot(), v.item(), t, v.last()));
		}
		slots.clear();
		slots.addAll(withCount);

		if (!title.getString().isEmpty()) {
			TextDisplayElement t = new TextDisplayElement(title.copy().withColor(0x404040));
			t.setBackground(0);
			t.setShadow(false);
			float sc = U * 40f;
			setup(t, new Vector3f(x(w / 2f), y(14), 0.02f), new Vector3f(sc, sc, sc));
		}
		updateItems();
		updateOffset(true);
	}

	private static BlockState block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	private float x(float gx) {
		return (gx - w / 2f) * U;
	}

	private float y(float gy) {
		return (h / 2f - gy) * U;
	}

	/** Retângulo em pixels da tela (origem no canto de cima à esquerda). */
	private void rect(BlockState state, float gx, float gy, float gw, float gh, float z) {
		BlockDisplayElement e = new BlockDisplayElement(state);
		setup(e, new Vector3f(x(gx), y(gy + gh), z), new Vector3f(gw * U, gh * U, 0.001f));
	}

	private void setup(DisplayElement e, Vector3f translation, Vector3f scale) {
		e.setBillboardMode(Display.BillboardConstraints.CENTER);
		e.setTranslation(translation);
		e.setScale(scale);
		e.setViewRange(VIEW_RANGE);
		all.add(e);
		addElement(e);
	}

	@Override
	public boolean startWatching(ServerGamePacketListenerImpl conn) {
		if (owner != null && conn.player == owner) return false;
		return super.startWatching(conn);
	}

	@Override
	protected void onTick() {
		updateItems();
		updateOffset(false);
	}

	private void updateItems() {
		for (SlotView v : slots) {
			ItemStack now = v.slot().getItem();
			if (ItemStack.matches(now, v.last()[0])) continue;
			v.last()[0] = now.copy();
			v.item().setItem(now.copy());
			int n = now.getCount();
			v.count().setText(n > 1 ? Component.literal(String.valueOf(n)) : Component.empty());
		}
	}

	/** O painel fica do lado esquerdo do jogador (o lado onde ele mesmo vê a tela) e um pouco à frente. */
	private void updateOffset(boolean force) {
		float yaw = anchor.getYRot();
		if (!force && Math.abs(Mth.wrapDegrees(yaw - lastYaw)) < 4f) return;
		lastYaw = yaw;
		double r = Math.toRadians(yaw);
		Vec3 left = new Vec3(Math.cos(r), 0, Math.sin(r));
		Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
		Vec3 off = left.scale(0.95).add(fwd.scale(0.35)).add(0, anchor.getBbHeight() * 0.7, 0);
		for (DisplayElement e : all) e.setOffset(off);
	}

	public int elementCount() {
		return all.size();
	}
}
