package hudson.plugin.versioncolumn;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.Proc;
import hudson.Util;
import hudson.model.Computer;
import hudson.remoting.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import java.io.IOException;
import jenkins.security.MasterToSlaveCallable;
import jenkins.slaves.RemotingVersionInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.ArgumentMatchers;

@WithJenkins
class VersionMonitorTest {

    private static JenkinsRule j;

    private VersionMonitor versionMonitor;
    private VersionMonitor.DescriptorImpl descriptor;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @BeforeEach
    void createVersionMonitor() {
        versionMonitor = new VersionMonitor();
        descriptor = (VersionMonitor.DescriptorImpl) versionMonitor.getDescriptor();
    }

    @Test
    void testToHtml_NullVersion() {
        assertEquals("N/A", versionMonitor.toHtml(null));
    }

    @Test
    void testToHtml_SameVersion() {
        String remotingVersion = RemotingVersionInfo.getEmbeddedVersion().toString();
        assertEquals(Launcher.VERSION, remotingVersion);
        assertEquals(Launcher.VERSION, versionMonitor.toHtml(remotingVersion));
    }

    @Test
    void testToHtml_DifferentVersion() {
        String version = RemotingVersionInfo.getMinimumSupportedVersion().toString();
        assertEquals(Util.wrapToErrorSpan(version), versionMonitor.toHtml(version));
    }

    @Test
    void testDescriptorImplConstructor() {
        VersionMonitor.DescriptorImpl descriptorImpl = new VersionMonitor.DescriptorImpl();
        assertSame(VersionMonitor.DESCRIPTOR, descriptorImpl, "DESCRIPTOR should be set to this instance");
    }

    @Test
    void testGetDisplayName() {
        assertEquals("Remoting Version", descriptor.getDisplayName());
    }

    @Test
    void testMonitor_NullChannel() throws Exception {
        PretendSlave pretendAgent = j.createPretendSlave(new TestLauncher());
        Computer computer = pretendAgent.createComputer();
        assertNull(computer.getChannel()); // Pre-condition for next assertion
        assertEquals("unknown-version", descriptor.monitor(computer));
    }

    @Test
    void testMonitor_SameVersion() throws Exception {
        DumbSlave agent = j.createOnlineSlave();
        Computer computer = agent.getComputer();
        assertNotNull(computer.getChannel());
        assertEquals(Launcher.VERSION, descriptor.monitor(computer));
    }

    @Test
    void testMonitor_DifferentVersion_Ignored() throws IOException, InterruptedException {
        VersionMonitor.DescriptorImpl mockDescriptor = spy(new VersionMonitor.DescriptorImpl());
        doReturn(true).when(mockDescriptor).isIgnored(); // Ensure isIgnored returns true.

        Computer computer = mock(Computer.class);
        VirtualChannel channel = mock(VirtualChannel.class);
        String differentVersion = "different-version";

        when(computer.getChannel()).thenReturn(channel);
        when(channel.call(ArgumentMatchers.<MasterToSlaveCallable<String, IOException>>any()))
                .thenReturn(differentVersion);
        when(computer.isOffline()).thenReturn(false);

        String result = mockDescriptor.monitor(computer);

        assertEquals(differentVersion, result);
        verify(computer, never()).setTemporarilyOffline(anyBoolean(), any());
    }

    @Test
    void testMonitor_DifferentVersion_NotIgnored() throws IOException, InterruptedException {
        VersionMonitor.DescriptorImpl mockDescriptor = spy(new VersionMonitor.DescriptorImpl());
        doReturn(false).when(mockDescriptor).isIgnored(); // Ensure isIgnored returns false

        Computer computer = mock(Computer.class);
        VirtualChannel channel = mock(VirtualChannel.class);
        String differentVersion = "different-version"; // Different from Launcher.VERSION

        // Set up the computer and channel behavior
        when(computer.getChannel()).thenReturn(channel);
        when(computer.getName()).thenReturn("test-computer");
        when(channel.call(ArgumentMatchers.<MasterToSlaveCallable<String, IOException>>any()))
                .thenReturn(differentVersion);

        String result = mockDescriptor.monitor(computer);

        assertEquals(differentVersion, result);

        verify(computer).setTemporaryOfflineCause(any(VersionMonitor.RemotingVersionMismatchCause.class));
    }

    @Test
    void testMonitor_VersionIsNull_Ignored() throws IOException, InterruptedException {
        VersionMonitor.DescriptorImpl mockDescriptor = spy(new VersionMonitor.DescriptorImpl());
        doReturn(true).when(mockDescriptor).isIgnored(); // Ensure isIgnored returns true.

        Computer computer = mock(Computer.class);
        VirtualChannel channel = mock(VirtualChannel.class);
        VersionMonitor.RemotingVersionMismatchCause cause = mock(VersionMonitor.RemotingVersionMismatchCause.class);

        when(computer.getChannel()).thenReturn(channel);
        when(channel.call(ArgumentMatchers.<MasterToSlaveCallable<String, IOException>>any()))
                .thenReturn(null);
        when(computer.isOffline()).thenReturn(true);
        when(computer.getOfflineCause()).thenReturn(cause);

        String result = mockDescriptor.monitor(computer);

        assertNull(result);
        verify(computer).setTemporarilyOffline(eq(false), isNull());
    }

    @Test
    void testMonitor_OfflineDueToMismatch_VersionsMatch() throws IOException, InterruptedException {
        Computer computer = mock(Computer.class);
        VirtualChannel channel = mock(VirtualChannel.class);
        VersionMonitor.RemotingVersionMismatchCause cause = new VersionMonitor.RemotingVersionMismatchCause("Mismatch");

        when(computer.getChannel()).thenReturn(channel);
        when(channel.call(ArgumentMatchers.<MasterToSlaveCallable<String, IOException>>any()))
                .thenReturn(Launcher.VERSION);
        when(computer.isOffline()).thenReturn(true);
        when(computer.getOfflineCause()).thenReturn(cause);

        String result = descriptor.monitor(computer);

        assertEquals(Launcher.VERSION, result);
        verify(computer).setTemporarilyOffline(eq(false), isNull());
    }

    @Test
    void testMonitor_OfflineDueToOtherCause() throws IOException, InterruptedException {
        Computer computer = mock(Computer.class);
        VirtualChannel channel = mock(VirtualChannel.class);
        OfflineCause otherCause = mock(OfflineCause.class);

        when(computer.getChannel()).thenReturn(channel);
        when(channel.call(ArgumentMatchers.<MasterToSlaveCallable<String, IOException>>any()))
                .thenReturn(Launcher.VERSION);
        when(computer.isOffline()).thenReturn(true);
        when(computer.getOfflineCause()).thenReturn(otherCause);

        String result = descriptor.monitor(computer);

        assertEquals(Launcher.VERSION, result);
        verify(computer, never()).setTemporarilyOffline(eq(false), any());
    }

    @Test
    void testRemotingVersionMismatchCause() {
        String message = "Version mismatch";
        VersionMonitor.RemotingVersionMismatchCause cause = new VersionMonitor.RemotingVersionMismatchCause(message);

        assertEquals(message, cause.toString());
        assertEquals(VersionMonitor.class, cause.getTrigger());
    }

    private static class TestLauncher implements FakeLauncher {

        @Override
        public Proc onLaunch(hudson.Launcher.ProcStarter p) {
            throw new UnsupportedOperationException("Unsupported run.");
        }
    }
}
