package com.freya02.bot.tag;

import com.freya02.bot.Database;
import com.freya02.bot.utils.Utils;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class TagDB {
	private final Database database;

	public TagDB(Database database) throws SQLException {
		this.database = database;

		final String setupSql = Utils.readResource("TagDB.sql");
		try (Connection connection = database.getConnection()) {
			//TODO need to fill restrictions
			// see how to make alter tables so restrictions are kept up-to-date
			//   internal checks with methods checkName, checkDescription, checkText
			final PreparedStatement statement = connection.prepareStatement(setupSql);

			//Setting column restrictions to JDA constants
			statement.setObject(1, OptionData.MAX_CHOICE_NAME_LENGTH);
			statement.setObject(2, MessageEmbed.VALUE_MAX_LENGTH);
			statement.setObject(3, Message.MAX_CONTENT_LENGTH);

			statement.execute();
		}
	}

	public void create(long guildId, long ownerId, String name, String description, String text) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					//TODO use text blocks
					"insert into Tag (guildid, ownerid, name, description, text) " +
							"values (?, ?, ?, ?, ?)"
			);

			//TODO abstraction
			statement.setLong(1, guildId);
			statement.setLong(2, ownerId);
			statement.setString(3, name);
			statement.setString(4, description);
			statement.setString(5, text);

			statement.executeUpdate();
		}
	}

	public void edit(long guildId, long ownerId, String name, String description, String text) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					"update Tag set description = ?, text = ? " +
							"where guildid = ? and ownerid = ? and name = ?"
			);

			statement.setString(1, description);
			statement.setString(2, text);
			statement.setLong(3, guildId);
			statement.setLong(4, ownerId);
			statement.setString(5, name);

			statement.executeUpdate();
		}
	}

	public void delete(long guildId, long ownerId, String name) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					"delete from Tag " +
							"where guildid = ? and ownerid = ? and name = ?"
			);

			statement.setLong(1, guildId);
			statement.setLong(2, ownerId);
			statement.setString(3, name);

			statement.executeUpdate();
		}
	}

	@Nullable
	public Tag get(long guildId, String name) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					"select * from Tag " +
							"where guildid = ? and name = ?"
			);

			statement.setLong(1, guildId);
			statement.setString(2, name);

			final ResultSet set = statement.executeQuery();

			if (!set.next()) return null;

			return Tag.fromResult(set);
		}
	}

	public void incrementTag(long guildId, String name) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					"update Tag set uses = uses + 1 " +
							"where guildid = ? and name = ?"
			);

			statement.setLong(1, guildId);
			statement.setString(2, name);

			statement.executeUpdate();
		}
	}

	@NotNull
	private List<ShortTag> readShortTags(PreparedStatement statement) throws SQLException {
		final List<ShortTag> list = new ArrayList<>();
		final ResultSet set = statement.executeQuery();

		while (set.next()) {
			list.add(new ShortTag(
					set.getString("name"),
					set.getString("description")
			));
		}

		return list;
	}

	public int getTotalTags(long guildId) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					"select count(*) from Tag where guildid = ?"
			);

			statement.setLong(1, guildId);

			final ResultSet totalSet = statement.executeQuery();
			totalSet.next();

			return totalSet.getInt(1);
		}
	}

	public List<Tag> getTagRange(long guildId, TagCriteria criteria, int offset, int amount) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					"select * from Tag " +
							"where guildid = ? " +
							"order by " + criteria.getKey() + " " +
							"offset ? " +
							"limit ?"
			);

			statement.setLong(1, guildId);
			statement.setInt(2, offset);
			statement.setInt(3, amount);

			final List<Tag> list = new ArrayList<>();
			final ResultSet set = statement.executeQuery();

			while (set.next()) {
				list.add(Tag.fromResult(set));
			}

			return list;
		}
	}

	public List<ShortTag> getShortTagsSorted(long guildId, TagCriteria criteria) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					"select name, description from Tag " +
							"where guildid = ? " +
							"order by " + criteria.getKey()
			);
			statement.setLong(1, guildId);

			return readShortTags(statement);
		}
	}

	public List<ShortTag> getShortTagsSorted(long guildId, long ownerId, TagCriteria criteria) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					"select name, description from Tag " +
							"where guildid = ? and ownerid = ? " +
							"order by " + criteria.getKey()
			);
			statement.setLong(1, guildId);
			statement.setLong(2, ownerId);

			return readShortTags(statement);
		}
	}

	public long getRank(long guildId, String name) throws SQLException {
		try (Connection connection = database.getConnection()) {
			final PreparedStatement statement = connection.prepareStatement(
					"select rank from " +
							"(select name, dense_rank() over (order by uses desc) as rank from Tag " +
							"where guildid = ?) as ranks " +
							"where name = ?"
			);
			statement.setLong(1, guildId);
			statement.setString(2, name);

			final ResultSet set = statement.executeQuery();
			if (!set.next()) throw new NoSuchElementException();

			return set.getLong("rank");
		}
	}
}
