classDiagram
class VoiceChatManager{
+startVoiceChat()
+stopVoiceChat()
+manageParticipants()
-audioSessions
}

    class AudioEncoder{
        +encode(byte[] rawAudio): byte[]
        +decode(byte[] encoded): byte[]
        +setCodec(String codecType)
    }
    
    class Packetizer{
        +packetize(byte[] audioData): List<AudioPacket>
        +depacketize(List<AudioPacket>): byte[]
        +setPacketSize(int size)
    }
    
    VoiceChatManager --> AudioEncoder
    VoiceChatManager --> Packetizer