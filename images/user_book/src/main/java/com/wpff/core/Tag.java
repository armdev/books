package com.wpff.core;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import io.swagger.annotations.ApiModelProperty;

/**
 * Named query to select all tags
 */
@Entity
@Table(name = "tag")
@NamedQueries({ @NamedQuery(name = "com.wpff.core.Tag.findAll", query = "SELECT u FROM Tag u") })
/**
 * Tag class
 */
public class Tag {

	/**
	 * Tag ID.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "tag_id", unique = true, nullable = false)
	@ApiModelProperty(hidden = true)
	private int id;

	/**
	 * Name of Tag
	 */
	@Column(name = "name", unique = true, nullable = false)
	private String name;

	/**
	 * Data for the tag. May be empty
	 */
	@Column(name = "data", nullable = true)
	private String data;

	/**
	 * Default constructor
	 */
	public Tag() {
	}

	public Tag(String name, int id, String data) {
		this.name = name;
		this.id = id;
		this.data = data;
	}

	public String toString() {
		return "Tag[id=" + id + ", " + "name='" + name + "']";
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	// @ApiModelProperty(hidden=true)
	public int getId() {
		return this.id;
	}

	// @ApiModelProperty(hidden=true)
	public void setId(int id) {
		this.id = id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Tag)) {
			return false;
		}

		final Tag that = (Tag) o;

		return Objects.equals(this.name, that.name) && Objects.equals(this.id, that.id)
				&& Objects.equals(this.data, that.data);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.id, this.data);
	}
}
