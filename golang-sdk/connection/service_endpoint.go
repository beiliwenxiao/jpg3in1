package connection

import "strconv"

// ServiceEndpoint 服务端点
type ServiceEndpoint struct {
	ServiceID string
	Name      string
	Address   string
	Port      int
	Protocol  string
	Metadata  map[string]string
}

// Key 返回端点的唯一标识
func (e *ServiceEndpoint) Key() string {
	return e.Address + ":" + strconv.Itoa(e.Port)
}
