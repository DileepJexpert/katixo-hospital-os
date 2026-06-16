import 'dart:async';

import 'package:web_socket_channel/status.dart' as ws_status;
import 'package:web_socket_channel/web_socket_channel.dart';

/// Lightweight client for the real-time board feed (`/ws/board`). Connects with
/// the JWT as a query param, and calls [onTopic] with the topic name ("queue",
/// "beds", "pharmacy") on each server nudge — the screen then re-fetches its
/// REST data. Auto-reconnects with a fixed backoff. Purely additive: board
/// screens keep their safety poll, so if the socket never connects nothing
/// breaks — updates just aren't instant.
class BoardSocket {
  BoardSocket({
    required this.baseUrl,
    required this.token,
    required this.onTopic,
  });

  final String baseUrl;
  final String token;
  final void Function(String topic) onTopic;

  WebSocketChannel? _channel;
  StreamSubscription<dynamic>? _sub;
  Timer? _reconnect;
  bool _closed = false;

  void connect() {
    _closed = false;
    _open();
  }

  void _open() {
    if (_closed) return;
    try {
      // http -> ws, https -> wss
      final wsBase = baseUrl.replaceFirst('http', 'ws');
      final uri = Uri.parse('$wsBase/ws/board?token=$token');
      final channel = WebSocketChannel.connect(uri);
      _channel = channel;
      _sub = channel.stream.listen(
        (data) {
          final topic = _parseTopic('$data');
          if (topic != null) onTopic(topic);
        },
        onDone: _scheduleReconnect,
        onError: (_) => _scheduleReconnect(),
        cancelOnError: true,
      );
    } catch (_) {
      _scheduleReconnect();
    }
  }

  String? _parseTopic(String raw) {
    final m = RegExp(r'"topic"\s*:\s*"(\w+)"').firstMatch(raw);
    return m?.group(1);
  }

  void _scheduleReconnect() {
    _sub?.cancel();
    _sub = null;
    _channel = null;
    if (_closed) return;
    _reconnect?.cancel();
    _reconnect = Timer(const Duration(seconds: 5), _open);
  }

  void dispose() {
    _closed = true;
    _reconnect?.cancel();
    _sub?.cancel();
    _channel?.sink.close(ws_status.normalClosure);
  }
}
