/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package tntrun.signs.type;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import tntrun.FormattingCodesParser;
import tntrun.TNTRun;
import tntrun.arena.Arena;
import tntrun.messages.Messages;

public class LeaveSign implements SignType {

	private TNTRun plugin;

	public LeaveSign(TNTRun plugin) {
		this.plugin = plugin;
	}

	@Override
	public void handleCreation(SignChangeEvent e) {
		e.setLine(0, FormattingCodesParser.parseFormattingCodes(plugin.getConfig().getString("signs.prefix")));
		Messages.sendMessage(e.getPlayer(), Messages.trprefix + Messages.signcreate);
	}

	@Override
	public void handleClick(PlayerInteractEvent e) {
		Arena arena = plugin.amanager.getPlayerArena(e.getPlayer().getName());
		if (arena != null) {
			arena.getPlayerHandler().leavePlayer(e.getPlayer(), Messages.playerlefttoplayer, Messages.playerlefttoothers);
			e.setCancelled(true);
		} else {
			Messages.sendMessage(e.getPlayer(), Messages.trprefix + Messages.playernotinarena);
		}
	}

	@Override
	public void handleDestroy(BlockBreakEvent e) {
	}

}
