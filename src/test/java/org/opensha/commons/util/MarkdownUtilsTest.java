package org.opensha.commons.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for {@link MarkdownUtils#renderMarkdownToHtml(String)}: the lean
 * Markdown&rarr;HTML render used by the update prompt's release-notes view. The
 * Swing rendering itself is not exercised here (consistent with
 * {@code SwingUpdatePrompt} not being unit-tested); only the HTML output of the
 * helper is checked.
 *
 * @author Akash Bhatthal
 */
@SuppressWarnings("javadoc")
public class MarkdownUtilsTest {

	@Test
	public final void testRenderMarkdownToHtmlNullAndBlank() {
		assertEquals("", MarkdownUtils.renderMarkdownToHtml(null));
		assertEquals("", MarkdownUtils.renderMarkdownToHtml(""));
		assertEquals("", MarkdownUtils.renderMarkdownToHtml("   \n\t "));
	}

	@Test
	public final void testRenderMarkdownToHtmlBold() {
		String html = MarkdownUtils.renderMarkdownToHtml("**bold**");
		assertNotNull(html);
		assertTrue("expected <strong> for bold, got: " + html, html.contains("<strong>bold</strong>"));
	}

	@Test
	public final void testRenderMarkdownToHtmlHeading() {
		String html = MarkdownUtils.renderMarkdownToHtml("# Heading");
		assertNotNull(html);
		assertTrue("expected <h1>, got: " + html, html.contains("<h1>Heading</h1>"));
	}

	@Test
	public final void testRenderMarkdownToHtmlListItem() {
		String html = MarkdownUtils.renderMarkdownToHtml("- item");
		assertNotNull(html);
		assertTrue("expected <li>, got: " + html, html.contains("<li>item</li>"));
	}

	@Test
	public final void testRenderMarkdownToHtmlTable() {
		String md = "| a | b |\n| --- | --- |\n| 1 | 2 |\n";
		String html = MarkdownUtils.renderMarkdownToHtml(md);
		assertNotNull(html);
		assertTrue("expected a <table> for a GFM table, got: " + html, html.contains("<table>"));
		assertTrue("expected <td>1</td>, got: " + html, html.contains("<td>1</td>"));
	}

	// ---- sectionFromHeading ----

	@Test
	public final void testSectionFromHeadingNullAndBlank() {
		assertNull(MarkdownUtils.sectionFromHeading(null, "Release Notes"));
		String blank = "   \n\t ";
		assertSame(blank, MarkdownUtils.sectionFromHeading(blank, "Release Notes"));
	}

	@Test
	public final void testSectionFromHeadingReturnsWholeWhenAbsent() {
		String md = "Preamble line.\nMore preamble.";
		assertSame(md, MarkdownUtils.sectionFromHeading(md, "Release Notes"));
	}

	@Test
	public final void testSectionFromHeadingTrimsPreamble() {
		String md = "Full changelog: https://example.com\n\n"
				+ "## Release Notes\n\n- fix A\n- fix B\n";
		String out = MarkdownUtils.sectionFromHeading(md, "Release Notes");
		assertNotNull(out);
		assertTrue("should start at the heading, got: " + out, out.startsWith("## Release Notes"));
		assertFalse("preamble should be dropped, got: " + out, out.contains("Full changelog"));
		assertTrue("should keep the notes below, got: " + out, out.contains("- fix A"));
		assertTrue("should keep the notes below, got: " + out, out.contains("- fix B"));
	}

	@Test
	public final void testSectionFromHeadingIsCaseInsensitive() {
		String md = "# release notes\nbody";
		String out = MarkdownUtils.sectionFromHeading(md, "Release Notes");
		assertTrue("case-insensitive match, got: " + out, out.startsWith("# release notes"));
	}

	@Test
	public final void testSectionFromHeadingAcceptsClosingHashRun() {
		String md = "preamble\n## Release Notes ##\nbody";
		String out = MarkdownUtils.sectionFromHeading(md, "Release Notes");
		assertTrue("should match heading with closing #, got: " + out, out.startsWith("## Release Notes ##"));
	}
}