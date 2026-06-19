package service

type HealthService struct{}

func NewHealthService() HealthService {
	return HealthService{}
}

func (service HealthService) Status() string {
	return "ok"
}
