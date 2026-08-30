package main

/*
#include <stdlib.h>
*/
import "C"
import (
	"context"
	"io"
	"log"
	"net"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
	"unsafe"

	"tailscale.com/ipn/ipnstate"
	"tailscale.com/tsnet"
)

var (
	server *tsnet.Server
	ready  bool
	status string = "INITIALIZING"
	mu     sync.Mutex

	activeRemoteAddr string = "100.89.137.102:25565" // Default to Node 1 Main Stage (port 25565)
	activeMu         sync.Mutex

	
	// Phase 4: XOR Obfuscated Key
	// XOR Key: "SajiloSystem_Network"
	obfuscatedKey = [ ]byte{
		0x27, 0x12, 0x01, 0x0c, 0x15, 0x42, 0x32, 0x0c, 0x07, 0x1c, 0x48, 0x06, 0x05, 0x02, 0x12, 0x25,
		0x31, 0x3a, 0x39, 0x31, 0x04, 0x50, 0x5b, 0x2a, 0x22, 0x3b, 0x01, 0x35, 0x5e, 0x27, 0x24, 0x0b,
		0x2d, 0x0b, 0x0d, 0x42, 0x14, 0x25, 0x3a, 0x03, 0x6b, 0x2f, 0x3a, 0x58, 0x3f, 0x3a, 0x04, 0x16,
		0x07, 0x33, 0x0d, 0x5c, 0x07, 0x06, 0x21, 0x3e, 0x04, 0x01, 0x37, 0x5a, 0x3d,
	}
)

const (
	targetIP = "127.0.0.1"
	remoteIP = "100.89.137.102"
)

func getAuthKey() string {
	xorKey := "SajiloSystem_Network"
	result := make([ ]byte, len(obfuscatedKey))
	for i := 0; i < len(obfuscatedKey); i++ {
		result[i] = obfuscatedKey[i] ^ xorKey[i%len(xorKey)]
	}
	return string(result)
}


func initLog() {
	dir, err := os.UserCacheDir()
	if err != nil {
		dir = os.TempDir()
	}
	logDir := filepath.Join(dir, "nml")
	if err := os.MkdirAll(logDir, 0755); err != nil {
		log.SetOutput(os.Stderr)
		return
	}

	f, err := os.OpenFile(filepath.Join(logDir, "nml_gateway.log"), os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err == nil {
		mw := io.MultiWriter(os.Stderr, f)
		log.SetOutput(mw)
	} else {
		log.SetOutput(os.Stderr)
	}
}

//export StartProxy
func StartProxy(cKey *C.char) {
	key := C.GoString(cKey)
	if key == "" || key == "USE_INTERNAL" || key == "DUMMY" {
		key = getAuthKey()
	}
	log.Printf("Starting bridge with key length: %d\n", len(key))
	go runBridge(key)
}
    

//export IsReady
func IsReady() C.int {
	mu.Lock()
	defer mu.Unlock()
	if ready {
		return 1
	}
	return 0
}

//export GetStatus
func GetStatus() *C.char {
	mu.Lock()
	defer mu.Unlock()
	return C.CString(status)
}

//export FreeStatus
func FreeStatus(s *C.char) {
	C.free(unsafe.Pointer(s))
}

//export UpdateRemoteAddr
func UpdateRemoteAddr(cTarget *C.char) {
	target := strings.ToLower(strings.TrimSpace(C.GoString(cTarget)))
	activeMu.Lock()
	defer activeMu.Unlock()
	if target == "minigames" || target == "node-1" {
		activeRemoteAddr = "100.89.137.102:25565"
		log.Printf("[Go Bridge] Repointing target to node-1 (100.89.137.102:25565)\n")
	} else if target == "lobby" || target == "node-2" {
		activeRemoteAddr = "100.120.244.95:25565"
		log.Printf("[Go Bridge] Repointing target to node-2 (100.120.244.95:25565)\n")
	} else {
		log.Printf("[Go Bridge] Received unknown target payload: %s, keeping current\n", target)
	}
}

func runBridge(key string) {
	initLog()
	log.Println("--- SECURE NETWORK LAYER STARTING ---")

	// Force login to prevent "NoState" hang
	os.Setenv("TSNET_FORCE_LOGIN", "1")

	dir, _ := os.UserCacheDir()
	tsDir := filepath.Join(dir, "NML_Member_State")
	os.MkdirAll(tsDir, 0755)

	server = &tsnet.Server{
		AuthKey:   key,
		Dir:       tsDir,
		Hostname:  "NML-Phoenix",
		Logf:      log.Printf,
		Ephemeral: true,
	}

	errTCP := startGhostListener(targetIP+":25565", remoteIP+":25565")
	if errTCP != nil {
		updateStatus("FATAL_PORT_BIND")
		return
	}

	go startUDPSessionManager(targetIP+":24454", remoteIP+":24454")

	if err := server.Start(); err != nil {
		updateStatus("START_FAILED")
		return
	}

	for {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		log.Println("Syncing network state...")
		st, err := server.Up(ctx)
		cancel()
		if err == nil && st != nil {
			mu.Lock()
			ready = true
			status = "STABLE"
			mu.Unlock()
			log.Println(">>> GATEWAY ONLINE <<<")
			go monitorConnection()
			break
		}
		time.Sleep(2 * time.Second)
	}
}

func updateStatus(s string) {
	mu.Lock()
	defer mu.Unlock()
	status = s
}

func startGhostListener(localAddr, defaultRemoteAddr string) error {
	l, err := net.Listen("tcp", localAddr)
	if err != nil {
		return err
	}
	go func() {
		for {
			client, err := l.Accept()
			if err != nil {
				continue
			}
			go func(c net.Conn) {
				defer c.Close()
				for i := 0; i < 60; i++ {
					if isBridgeReady() {
						break
					}
					time.Sleep(500 * time.Millisecond)
				}
				if !isBridgeReady() {
					return
				}
				activeMu.Lock()
				remoteAddr := activeRemoteAddr
				activeMu.Unlock()
				ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
				defer cancel()
				remote, err := server.Dial(ctx, "tcp", remoteAddr)
				if err != nil {
					return
				}
				done := make(chan struct{}, 2)
				go func() { io.Copy(remote, c); done <- struct{}{} }()
				go func() { io.Copy(c, remote); done <- struct{}{} }()
				<-done
				remote.Close()
			}(client)
		}
	}()
	return nil
}

func isBridgeReady() bool {
	mu.Lock()
	defer mu.Unlock()
	return ready
}

func startUDPSessionManager(localAddr, remoteAddr string) {
	addr, _ := net.ResolveUDPAddr("udp", localAddr)
	conn, err := net.ListenUDP("udp", addr)
	if err != nil {
		return
	}
	var remoteConn net.Conn
	var rmu sync.Mutex
	var lastClientAddr *net.UDPAddr
	lastActivity := time.Now()
	go func() {
		buf := make([]byte, 4096)
		for {
			n, clientAddr, err := conn.ReadFromUDP(buf)
			if err != nil || !isBridgeReady() {
				continue
			}
			rmu.Lock()
			lastClientAddr = clientAddr
			if remoteConn == nil {
				remoteConn, err = server.Dial(context.Background(), "udp", remoteAddr)
				if err != nil {
					rmu.Unlock()
					continue
				}
				go func(rc net.Conn) {
					defer rc.Close()
					rBuf := make([]byte, 4096)
					for {
						rn, err := rc.Read(rBuf)
						if err != nil {
							rmu.Lock()
							if remoteConn == rc {
								remoteConn = nil
							}
							rmu.Unlock()
							return
						}
						rmu.Lock()
						cAddr := lastClientAddr
						rmu.Unlock()
						if cAddr != nil {
							conn.WriteToUDP(rBuf[:rn], cAddr)
						}
					}
				}(remoteConn)
			}
			lastActivity = time.Now()
			remoteConn.Write(buf[:n])
			rmu.Unlock()
		}
	}()
	go func() {
		for {
			time.Sleep(10 * time.Second)
			rmu.Lock()
			if remoteConn != nil && time.Since(lastActivity) > 30*time.Second {
				remoteConn.Close()
				remoteConn = nil
			}
			rmu.Unlock()
		}
	}()
}

func getRelayLocation(code string) string {
	code = strings.ToLower(code)
	if strings.Contains(code, "blr") || strings.Contains(code, "del") || strings.Contains(code, "bom") || strings.Contains(code, "ccu") {
		return "India"
	}
	if strings.Contains(code, "sin") {
		return "Singapore"
	}
	if strings.Contains(code, "tok") || strings.Contains(code, "nrt") {
		return "Japan"
	}
	if strings.Contains(code, "hkg") {
		return "Hong Kong"
	}
	if strings.Contains(code, "syd") {
		return "Australia"
	}
	if strings.Contains(code, "fra") {
		return "Germany"
	}
	if strings.Contains(code, "lhr") {
		return "United Kingdom"
	}
	return strings.ToUpper(code)
}

func monitorConnection() {
	for {
		time.Sleep(3 * time.Second)
		if server == nil {
			continue
		}
		lc, err := server.LocalClient()
		if err != nil {
			continue
		}
		statusObj, err := lc.Status(context.Background())
		if err != nil || statusObj == nil {
			continue
		}

		activeMu.Lock()
		target := activeRemoteAddr
		activeMu.Unlock()

		targetIP := "100.89.137.102"
		if strings.Contains(target, "100.120.244.95") {
			targetIP = "100.120.244.95"
		}

		found := false
		var targetPeerStatus *ipnstate.PeerStatus

		for _, ps := range statusObj.Peer {
			for _, ip := range ps.TailscaleIPs {
				if ip.String() == targetIP {
					targetPeerStatus = ps
					found = true
					break
				}
			}
			if found {
				break
			}
		}

		mu.Lock()
		if !ready {
			mu.Unlock()
			continue
		}
				if found && targetPeerStatus != nil {
			if targetPeerStatus.Active {
				if targetPeerStatus.CurAddr != "" {
					status = "DIRECT"
				} else if targetPeerStatus.Relay != "" {
					status = "FORWARDED:" + getRelayLocation(targetPeerStatus.Relay)
				} else {
					status = "STABLE"
				}
			} else {
				status = "STABLE"
			}
		} else {
			status = "STABLE"
		}
		mu.Unlock()
	}
}

func main() {}