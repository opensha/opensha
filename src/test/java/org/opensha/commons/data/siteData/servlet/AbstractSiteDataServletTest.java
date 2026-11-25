package org.opensha.commons.data.siteData.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.siteData.ServletEnabledSiteData;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.BooleanParameter;

public class AbstractSiteDataServletTest {

	@SuppressWarnings("unchecked")
	private final ServletEnabledSiteData<String> provider = mock(ServletEnabledSiteData.class);
	private final BooleanParameter option = new BooleanParameter("Example option", false);
	private AbstractSiteDataServlet<String> servlet;

	@Before
	public void configureProvider() {
		when(provider.getShortName()).thenReturn("Test");
		ParameterList parameters = new ParameterList();
		parameters.addParameter(option);
		when(provider.getServerSideParams()).thenReturn(parameters);
		servlet = new AbstractSiteDataServlet<String>(provider) {};
	}

	@Test
	public void readsAndWritesOutsideTheLockAndLooksUpUnderIt() throws Exception {
		Location expectedLocation = new Location(34, -118, 2);
		option.addParameterChangeListener(event -> assertTrue(Thread.holdsLock(provider)));
		when(provider.getValues(any(LocationList.class))).thenAnswer(invocation -> {
			assertTrue(Thread.holdsLock(provider));
			assertTrue(option.getValue());
			LocationList locations = invocation.getArgument(0);
			assertEquals(List.of(expectedLocation), locations);
			return new ArrayList<>(List.of("test value"));
		});

		ArrayList<double[]> coordinates = new ArrayList<>(List.of(new double[] { 34, -118, 2 }));
		try (ObjectInputStream response = submit(enabledOption(), coordinates)) {
			assertEquals(List.of("test value"), response.readObject());
		}
	}

	@Test
	public void preservesClosestLocationRequests() throws Exception {
		Location location = new Location(34, -118);
		Location closest = new Location(34.1, -118);
		when(provider.getClosestDataLocation(location)).thenReturn(closest);
		try (ObjectInputStream response = submit(enabledOption(), AbstractSiteDataServlet.OP_GET_CLOSEST, location)) {
			assertEquals(closest, response.readObject());
		}
	}

	@Test
	public void leavesProviderOptionsUnchangedForAnInvalidLocation() throws Exception {
		try (ObjectInputStream response = submit(enabledOption(), new double[] { 34 })) {
			assertEquals(Boolean.FALSE, response.readObject());
			assertTrue(((String) response.readObject()).contains("latitude, longitude, and optional depth"));
		}
		assertFalse(option.getValue());
		verify(provider, never()).getValue(any(Location.class));
	}

	@Test
	public void restoresProviderOptionsIfAnUpdateFails() throws Exception {
		option.addParameterChangeListener(event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				throw new IllegalStateException("Example dataset unavailable");
		});
		try (ObjectInputStream response = submit(enabledOption(), new double[] { 34, -118 })) {
			assertEquals(Boolean.FALSE, response.readObject());
			assertTrue(((String) response.readObject()).contains("Example dataset unavailable"));
		}
		assertFalse(option.getValue());
		verify(provider, never()).getValue(any(Location.class));
	}

	private ObjectInputStream submit(Object... values) throws Exception {
		ByteArrayOutputStream payload = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(payload)) {
			for (Object value : values)
				out.writeObject(value);
		}

		ByteArrayInputStream body = new ByteArrayInputStream(payload.toByteArray());
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getContentLengthLong()).thenReturn((long) payload.size());
		when(request.getInputStream()).thenReturn(new ServletInputStream() {
			@Override
			public int read() {
				assertOutsideLock();
				return body.read();
			}

			@Override
			public boolean isFinished() {
				return body.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener listener) {}
		});

		ByteArrayOutputStream result = new ByteArrayOutputStream();
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
			@Override
			public void write(int value) {
				assertOutsideLock();
				result.write(value);
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setWriteListener(WriteListener listener) {}
		});

		servlet.doPost(request, response);
		return new ObjectInputStream(new ByteArrayInputStream(result.toByteArray()));
	}

	private void assertOutsideLock() {
		assertFalse(Thread.holdsLock(servlet));
		assertFalse(Thread.holdsLock(provider));
	}

	private static ArrayList<Boolean> enabledOption() {
		return new ArrayList<>(List.of(Boolean.TRUE));
	}
}
