package models

import "time"

type User struct {
	ID        string    `json:"id"`
	Username  string    `json:"username"`
	Email     *string   `json:"email,omitempty"`
	AvatarURL *string   `json:"avatarUrl,omitempty"`
	Bio       *string   `json:"bio,omitempty"`
	Role      string    `json:"role"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type UserWithPassword struct {
	User
	PasswordHash string
}

type RefreshToken struct {
	ID        string
	UserID    string
	TokenHash string
	ExpiresAt time.Time
	RevokedAt *time.Time
	CreatedAt time.Time
}

type VideoRef struct {
	ID        string    `json:"id"`
	Source    string    `json:"source"`
	SourceURL string    `json:"sourceUrl"`
	Title     string    `json:"title"`
	CoverURL  *string   `json:"coverUrl,omitempty"`
	Maker     *string   `json:"maker,omitempty"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type Favorite struct {
	ID            string     `json:"id"`
	UserID        string     `json:"-"`
	VideoRef      VideoRef   `json:"videoRef"`
	TitleSnapshot string     `json:"titleSnapshot"`
	CoverSnapshot *string    `json:"coverSnapshot,omitempty"`
	MakerSnapshot *string    `json:"makerSnapshot,omitempty"`
	TagsSnapshot  []string   `json:"tagsSnapshot"`
	CreatedAt     time.Time  `json:"createdAt"`
	UpdatedAt     time.Time  `json:"updatedAt"`
	DeletedAt     *time.Time `json:"deletedAt,omitempty"`
}

type Comment struct {
	ID         string     `json:"id"`
	UserID     string     `json:"userId"`
	Username   string     `json:"username"`
	VideoRefID string     `json:"videoRefId"`
	ParentID   *string    `json:"parentId,omitempty"`
	Body       string     `json:"body"`
	Status     string     `json:"status"`
	LikeCount  int        `json:"likeCount"`
	CreatedAt  time.Time  `json:"createdAt"`
	UpdatedAt  time.Time  `json:"updatedAt"`
	DeletedAt  *time.Time `json:"deletedAt,omitempty"`
}

type Collection struct {
	ID            string    `json:"id"`
	OwnerID       string    `json:"ownerId"`
	OwnerUsername string    `json:"ownerUsername"`
	Title         string    `json:"title"`
	Description   string    `json:"description"`
	CoverURL      *string   `json:"coverUrl,omitempty"`
	Visibility    string    `json:"visibility"`
	Slug          string    `json:"slug"`
	ItemCount     int       `json:"itemCount"`
	LikeCount     int       `json:"likeCount"`
	SaveCount     int       `json:"saveCount"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt"`
}

type CollectionItem struct {
	ID           string    `json:"id"`
	CollectionID string    `json:"collectionId"`
	VideoRef     VideoRef  `json:"videoRef"`
	Note         string    `json:"note"`
	Position     int       `json:"position"`
	CreatedAt    time.Time `json:"createdAt"`
}

type CollectionDetail struct {
	Collection Collection       `json:"collection"`
	Items      []CollectionItem `json:"items"`
}

type FavoriteSnapshot struct {
	Title    string   `json:"title"`
	CoverURL *string  `json:"coverUrl,omitempty"`
	Maker    *string  `json:"maker,omitempty"`
	Tags     []string `json:"tags"`
}

type FavoriteSyncChange struct {
	SourceURL string           `json:"sourceUrl"`
	Source    string           `json:"source"`
	Op        string           `json:"op"`
	UpdatedAt time.Time        `json:"updatedAt"`
	Snapshot  FavoriteSnapshot `json:"snapshot"`
}

type FavoriteSyncResult struct {
	ServerTime time.Time  `json:"serverTime"`
	Changes    []Favorite `json:"changes"`
}
